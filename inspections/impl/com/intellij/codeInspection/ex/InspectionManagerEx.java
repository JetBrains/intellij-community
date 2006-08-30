/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

public class InspectionManagerEx extends InspectionManager implements JDOMExternalizable, ProjectComponent {
  private final UIOptions myUIOptions;
  private GlobalInspectionContextImpl myGlobalInspectionContext = null;
  private final Project myProject;
  @NonNls public String myCurrentProfileName;
  private ContentManager myContentManager = null;

  private Set<GlobalInspectionContextImpl> myRunningContexts = new HashSet<GlobalInspectionContextImpl>();

  public InspectionManagerEx(Project project) {
    myProject = project;
    myUIOptions = new UIOptions();
    myCurrentProfileName = "Default";
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }


  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(new TabbedPaneContentUI(), true, myProject);
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) { //headless environment
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      ToolWindow toolWindow =
        toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION, myContentManager.getComponent(), ToolWindowAnchor.BOTTOM);
      toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowInspection.png"));
      new ContentManagerWatcher(toolWindow, myContentManager);
    }
  }


  public void projectClosed() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.INSPECTION);
  }


  public UIOptions getUIOptions() {
    return myUIOptions;
  }

  

  public void readExternal(@NonNls Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(myUIOptions, element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(myUIOptions, element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @NotNull
  public CommonProblemDescriptor createProblemDescriptor(String descriptionTemplate, QuickFix... fixes) {
    return new CommonProblemDescriptorImpl(fixes, descriptionTemplate);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(PsiElement psiElement,
                                                   String descriptionTemplate,
                                                   LocalQuickFix fix,
                                                   ProblemHighlightType highlightType) {
    LocalQuickFix[] quickFixes = fix != null ? new LocalQuickFix[]{fix} : null;
    return createProblemDescriptor(psiElement, descriptionTemplate, quickFixes, highlightType);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(PsiElement psiElement,
                                                   String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   ProblemHighlightType highlightType) {
    return createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType, false);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(PsiElement psiElement,
                                                   String descriptionTemplate,
                                                   LocalQuickFix[] fixes,
                                                   ProblemHighlightType highlightType,
                                                   boolean isAfterEndOfLine) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine);
  }

  @NotNull
  public ProblemDescriptor createProblemDescriptor(PsiElement startElement,
                                                   PsiElement endElement,
                                                   String descriptionTemplate,
                                                   ProblemHighlightType highlightType,
                                                   LocalQuickFix... fixes) {
    return new ProblemDescriptorImpl(startElement, endElement, descriptionTemplate, fixes, highlightType, false);
  }

  @Nullable
  public static String getSuppressedInspectionIdsIn(PsiElement element) {
    if (element instanceof PsiComment) {
      String text = element.getText();
      Matcher matcher = GlobalInspectionContextImpl.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    if (element instanceof PsiDocCommentOwner) {
      PsiDocComment docComment = ((PsiDocCommentOwner)element).getDocComment();
      if (docComment != null) {
        PsiDocTag inspectionTag = docComment.findTagByName(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (inspectionTag != null && inspectionTag.getValueElement() != null) {
          String valueText = inspectionTag.getValueElement().getText();
          if (valueText != null) {
            return valueText;
          }
        }
      }
    }
    if (element instanceof PsiModifierListOwner) {
      Collection<String> suppressedIds = GlobalInspectionContextImpl.getInspectionIdsSuppressedInAnnotation((PsiModifierListOwner)element);
      return StringUtil.join(suppressedIds, ",");
    }
    return null;
  }

  public static boolean inspectionResultSuppressed(PsiElement place, LocalInspectionTool tool) {
    if (tool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)tool).isSuppressedFor(place);
    }

    return inspectionResultSuppressed(place, tool.getID());
  }

  public static boolean inspectionResultSuppressed(final PsiElement place, String toolId) {
    return getElementToolSuppressedIn(place, toolId) != null;
  }

  @Nullable
  public static PsiElement getElementToolSuppressedIn(PsiElement place, String toolId) {
    if (place == null) return null;
    PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class);
    if (statement != null) {
      PsiElement prev = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
      if (prev instanceof PsiComment) {
        String text = prev.getText();
        Matcher matcher = GlobalInspectionContextImpl.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
        if (matcher.matches() && GlobalInspectionContextImpl.isInspectionToolIdMentioned(matcher.group(1), toolId)) {
          return prev;
        }
      }
    }

    PsiLocalVariable local = PsiTreeUtil.getParentOfType(place, PsiLocalVariable.class);
    if (local != null && GlobalInspectionContextImpl.getAnnotationMemberSuppressedIn(local, toolId) != null) {
      PsiModifierList modifierList = local.getModifierList();
      return modifierList.findAnnotation(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    }

    PsiElement container = PsiTreeUtil.getNonStrictParentOfType(place, PsiDocCommentOwner.class);
    while(true) {
      if (!(container instanceof PsiTypeParameter)) break;
      container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
    }

    if (container != null) {
      PsiElement element = GlobalInspectionContextImpl.getElementMemberSuppressedIn((PsiDocCommentOwner)container, toolId);
      if (element != null) return element;
    }
    PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class, true);
    if (classContainer != null) {
      PsiElement element = GlobalInspectionContextImpl.getElementMemberSuppressedIn(classContainer, toolId);
      if (element != null) return element;
    }

    return null;
  }

  @NotNull
  public String getComponentName() {
    return "InspectionManager";
  }

  public GlobalInspectionContextImpl createNewGlobalContext(boolean reuse) {
    if (reuse) {
      if (myGlobalInspectionContext == null) {
        myGlobalInspectionContext = new GlobalInspectionContextImpl(myProject, myContentManager);
      }
      myRunningContexts.add(myGlobalInspectionContext);
      return myGlobalInspectionContext;
    }
    final GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(myProject, myContentManager);
    myRunningContexts.add(inspectionContext);
    return inspectionContext;
  }

  public void setProfile(final String name) {
    myCurrentProfileName = name;
  }

  public String getCurrentProfile() {
    return myCurrentProfileName;
  }

  public void closeRunningContext(GlobalInspectionContextImpl globalInspectionContext){
    myRunningContexts.remove(globalInspectionContext);
  }

  public Set<GlobalInspectionContextImpl> getRunningContexts() {
    return myRunningContexts;
  }

}
