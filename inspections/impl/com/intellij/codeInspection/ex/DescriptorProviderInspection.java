package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author max
 */
public abstract class DescriptorProviderInspection extends InspectionTool implements ProblemDescriptionsProcessor {
  private HashMap<RefEntity, CommonProblemDescriptor[]> myProblemElements;
  private HashMap<String, Set<RefElement>> myPackageContents = null;
  private HashSet<RefModule> myModulesProblems = null;
  private HashMap<CommonProblemDescriptor,RefEntity> myProblemToElements;
  private DescriptorComposer myComposer;
  private HashMap<RefEntity, Set<QuickFix>> myQuickFixActions;
  private HashMap<RefEntity, CommonProblemDescriptor[]> myIgnoredElements;

  private HashMap<RefEntity, CommonProblemDescriptor[]> myOldProblemElements = null;

  public void addProblemElement(RefEntity refElement, CommonProblemDescriptor[] descriptions) {
    if (refElement == null) return;
    if (descriptions == null || descriptions.length == 0) return;
    getProblemElements().put(refElement, descriptions);
    for (CommonProblemDescriptor description : descriptions) {
      getProblemToElements().put(description, refElement);
      collectQuickFixes(description.getFixes(), refElement);
    }
  }

  public Collection<CommonProblemDescriptor> getProblemDescriptors() {
    return getProblemToElements().keySet();
  }

  private void collectQuickFixes(final QuickFix[] fixes, final RefEntity refEntity) {
    if (fixes != null) {
      Set<QuickFix> localQuickFixes = getQuickFixActions().get(refEntity);
      if (localQuickFixes == null) {
        localQuickFixes = new HashSet<QuickFix>();
        getQuickFixActions().put(refEntity, localQuickFixes);
      }
      localQuickFixes.addAll(Arrays.asList(fixes));
    }
  }

  public void ignoreElement(RefEntity refEntity) {
    if (refEntity == null) return;
    ignoreProblemElement(refEntity);
    getQuickFixActions().remove(refEntity);
  }

  public void ignoreProblem(ProblemDescriptor problem) {
    RefEntity refElement = getProblemToElements().get(problem);
    if (refElement != null) ignoreProblem(refElement, problem, -1);
  }

  public void ignoreProblem(RefEntity refEntity, CommonProblemDescriptor problem, int idx) {
    if (refEntity == null) return;
    final Set<QuickFix> localQuickFixes = getQuickFixActions().get(refEntity);
    final QuickFix[] fixes = problem.getFixes();
    if (isIgnoreProblem(fixes, localQuickFixes, idx)){
      getProblemToElements().remove(problem);
      CommonProblemDescriptor[] descriptors = getProblemElements().get(refEntity);
      if (descriptors != null) {
        ArrayList<CommonProblemDescriptor> newDescriptors = new ArrayList<CommonProblemDescriptor>(Arrays.asList(descriptors));
        newDescriptors.remove(problem);
        getQuickFixActions().put(refEntity, null);
        if (newDescriptors.size() > 0) {
          getProblemElements().put(refEntity, newDescriptors.toArray(new ProblemDescriptor[newDescriptors.size()]));
          for (CommonProblemDescriptor descriptor : newDescriptors) {
            collectQuickFixes(descriptor.getFixes(), refEntity);
          }
        }
        else {
          ignoreProblemElement(refEntity);
        }
      }
    }
  }

  private void ignoreProblemElement(RefEntity refEntity){
    final CommonProblemDescriptor[] problemDescriptors = getProblemElements().remove(refEntity);
    getIgnoredElements().put(refEntity, problemDescriptors);
  }

  private static boolean isIgnoreProblem(QuickFix[] problemFixes, Set<QuickFix> fixes, int idx){
    if (problemFixes == null || fixes == null) {
      return true;
    }
    if (problemFixes.length <= idx){
      return true;
    }
    for (QuickFix fix : problemFixes) {
      if (fix != problemFixes[idx] && !fixes.contains(fix)){
        return false;
      }
    }
    return true;
  }

  public void cleanup() {
    super.cleanup();
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements == null) {
        myOldProblemElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
      }
      myOldProblemElements.clear();
      myOldProblemElements.putAll(getIgnoredElements());
      myOldProblemElements.putAll(getProblemElements());
    } else {
      myOldProblemElements = null;
    }

    myProblemElements = null;
    myProblemToElements = null;
    myQuickFixActions = null;
    myIgnoredElements = null;
    myPackageContents = null;
    myModulesProblems = null;
  }


  public void finalCleanup() {
    super.finalCleanup();
    myOldProblemElements = null;
  }

  public CommonProblemDescriptor[] getDescriptions(RefEntity refEntity) {
    if (refEntity instanceof RefElement && !((RefElement)refEntity).isValid()) {
      ignoreElement(refEntity);
      return null;
    }

    return getProblemElements().get(refEntity);
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new DescriptorComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(final RefEntity refEntity) {
        if (getProblemElements().containsKey(refEntity)) {
          CommonProblemDescriptor[] descriptions = getDescriptions(refEntity);
          for (CommonProblemDescriptor description : descriptions) {
            @NonNls final String template = description.getDescriptionTemplate();
            int line = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getLineNumber() : -1;
            final String text = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getPsiElement().getText() : "";
            @NonNls String problemText = template.replaceAll("#ref", text.replaceAll("\\$", "\\\\\\$"));
            problemText = problemText.replaceAll(" #loc ", " ");

            Element element = XMLExportUtl.createElement(refEntity, parentNode, line, description instanceof ProblemDescriptorImpl ? ((ProblemDescriptorImpl)description).getTextRange() : null);
            @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
            problemClassElement.addContent(getDisplayName());
            if (refEntity instanceof RefElement){
              final RefElement refElement = (RefElement)refEntity;
              final HighlightSeverity severity = getCurrentSeverity(refElement);
              final String attributeKey = getTextAttributeKey(refElement, severity, description instanceof ProblemDescriptor
                                                                                    ? ((ProblemDescriptor)description).getHighlightType()
                                                                                    : ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
              problemClassElement.setAttribute("severity", severity.myName);
              problemClassElement.setAttribute("attribute_key", attributeKey);
            }
            element.addContent(problemClassElement);
            try {
              Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
              descriptionElement.addContent(problemText);
              element.addContent(descriptionElement);
            }
            catch (IllegalDataException e) {
              //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
              System.out.println("Cannot save results for "
                                 + refEntity.getName());
            }
          }
        }
      }
    });
  }

  public boolean isGraphNeeded() {
    return false;
  }

  public boolean hasReportedProblems() {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_ONLY_DIFF){
      for (CommonProblemDescriptor descriptor : getProblemToElements().keySet()) {
        if (getProblemStatus(descriptor) != FileStatus.NOT_CHANGED){
          return true;
        }
      }
      if (myOldProblemElements != null){
        for (RefEntity entity : myOldProblemElements.keySet()) {
          if (entity instanceof RefElement && getElementStatus((RefElement)entity) != FileStatus.NOT_CHANGED){
            return true;
          }
        }
      }
      return false;
    } else {
      if (getProblemElements().size() > 0) return true;
    }
    return context != null &&
           context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN &&
           myOldProblemElements != null &&
           myOldProblemElements.size() > 0;
  }

  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefElement>>();
    myModulesProblems = new HashSet<RefModule>();
    final Set<RefEntity> elements = getProblemElements().keySet();
    for (RefEntity element : elements) {
      if (element instanceof RefElement) {
        String packageName = RefUtil.getInstance().getPackageName(element);
        Set<RefElement> content = myPackageContents.get(packageName);
        if (content == null) {
          content = new HashSet<RefElement>();
          myPackageContents.put(packageName, content);
        }
        content.add((RefElement)element);
      } else if (element instanceof RefModule){
        myModulesProblems.add((RefModule)element);
      }
    }
  }

  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    buildTreeNode(content, myPackageContents, getProblemElements());
    if (isOldProblemsIncluded(getContext())){
      HashMap<String, Set<RefElement>> oldContents = new HashMap<String, Set<RefElement>>();
      final Set<RefEntity> elements = myOldProblemElements.keySet();
      for (RefEntity element : elements) {
        if (element instanceof RefElement) {
          String packageName = RefUtil.getInstance().getPackageName(element);
          final Set<RefElement> collection = myPackageContents.get(packageName);
          if (collection != null){
            final Set<RefEntity> currentElements = new HashSet<RefEntity>(collection);
            if (contains((RefElement)element, currentElements)) continue;
          }
          Set<RefElement> oldContent = oldContents.get(packageName);
          if (oldContent == null) {
            oldContent = new HashSet<RefElement>();
            oldContents.put(packageName, oldContent);
          }
          oldContent.add((RefElement)element);
        }
      }
      buildTreeNode(content, oldContents, myOldProblemElements);
    }

    for (RefModule refModule : myModulesProblems) {
      InspectionModuleNode moduleNode = new InspectionModuleNode(refModule.getModule());
      final CommonProblemDescriptor[] problems = getProblemElements().get(refModule);
      for (CommonProblemDescriptor problem : problems) {
        moduleNode.add(new ProblemDescriptionNode(refModule, problem, !(this instanceof DuplicatePropertyInspection), this));
      }
      content.add(moduleNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  private boolean isOldProblemsIncluded(final GlobalInspectionContextImpl context) {
    return context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN && myOldProblemElements != null;
  }

  private void buildTreeNode(final List<InspectionTreeNode> content,
                             final HashMap<String, Set<RefElement>> packageContents,
                             final HashMap<RefEntity, CommonProblemDescriptor[]> problemElements) {
    final GlobalInspectionContextImpl context = getContext();
    Set<String> packages = packageContents.keySet();
    for (String p : packages) {
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = packageContents.get(p);
      for (RefElement refElement : elements) {
        if (context != null && context.getUIOptions().SHOW_ONLY_DIFF && getElementStatus(refElement) == FileStatus.NOT_CHANGED) continue;
        final RefElementNode elemNode = addNodeToParent(refElement, pNode);
        final CommonProblemDescriptor[] problems = problemElements.get(refElement);
        if (problems != null) {
          for (CommonProblemDescriptor problem : problems) {
            if (context != null && context.getUIOptions().SHOW_ONLY_DIFF && getProblemStatus(problem) == FileStatus.NOT_CHANGED) continue;
            elemNode.add(new ProblemDescriptionNode(refElement, problem, !(this instanceof DuplicatePropertyInspection), this));
          }
          if (problems.length == 1){
            elemNode.setProblem(problems[0]);
          }
        }
      }
      content.add(pNode);
    }
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public Set<RefModule> getModuleProblems() {
    return myModulesProblems;
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    if (refElements == null) return null;
    Map<Class, QuickFixAction> result = new java.util.HashMap<Class, QuickFixAction>();
    for (RefEntity refElement : refElements) {
      final Set<QuickFix> localQuickFixes = getQuickFixActions().get(refElement);
      if (localQuickFixes != null){
        for (QuickFix fix : localQuickFixes) {
          final Class klass = fix.getClass();
          final QuickFixAction quickFixAction = result.get(klass);
          if (quickFixAction != null){
            try {
              String familyName = fix.getFamilyName();
              familyName = familyName != null && familyName.length() > 0 ? "\'" + familyName + "\'" : familyName;
              ((LocalQuickFixWrapper)quickFixAction).setText(InspectionsBundle.message("inspection.descriptor.provider.apply.fix", familyName));
            }
            catch (AbstractMethodError e) {
              //for plugin compatibility
              ((LocalQuickFixWrapper)quickFixAction).setText(InspectionsBundle.message("inspection.descriptor.provider.apply.fix", ""));
            }
          } else {
            LocalQuickFixWrapper quickFixWrapper = new LocalQuickFixWrapper(fix, this);
            result.put(fix.getClass(), quickFixWrapper);
          }
        }
      }
    }
    return result.values().isEmpty() ? null : result.values().toArray(new QuickFixAction[result.size()]);
  }

  protected RefEntity getElement(CommonProblemDescriptor descriptor) {
    return getProblemToElements().get(descriptor);
  }

  public void ignoreProblem(final CommonProblemDescriptor descriptor, final QuickFix fix) {
    RefEntity refElement = getProblemToElements().get(descriptor);
    if (refElement != null) {
      final QuickFix[] fixes = descriptor.getFixes();
      for (int i = 0; i < fixes.length; i++) {
        if (fixes[i] == fix){
          ignoreProblem(refElement, descriptor, i);
          return;
        }
      }
    }
  }


  public boolean isElementIgnored(final RefElement element) {
    if (getIgnoredElements() == null) return false;
    for (RefEntity entity : getIgnoredElements().keySet()) {
      if (entity instanceof RefElement){
        final RefElement refElement = (RefElement)entity;
        if (Comparing.equal(refElement.getElement(), element.getElement())){
          return true;
        }
      }
    }
    return false;
  }

  public FileStatus getProblemStatus(final CommonProblemDescriptor descriptor) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements != null){
        final Set<CommonProblemDescriptor> allAvailable = new HashSet<CommonProblemDescriptor>();
        for (CommonProblemDescriptor[] descriptors : myOldProblemElements.values()) {
          if (descriptors != null) {
            allAvailable.addAll(Arrays.asList(descriptors));
          }
        }
        final boolean old = contains(descriptor, allAvailable);
        final boolean current = contains(descriptor, getProblemToElements().keySet());
        return calcStatus(old, current);
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  private static boolean contains(CommonProblemDescriptor descriptor, Collection<CommonProblemDescriptor> descriptors){
    PsiElement element = null;
    if (descriptor instanceof ProblemDescriptor){
      element = ((ProblemDescriptor)descriptor).getPsiElement();
    }
    for (CommonProblemDescriptor problemDescriptor : descriptors) {
      if (problemDescriptor instanceof ProblemDescriptor){
        if (!Comparing.equal(element, ((ProblemDescriptor)problemDescriptor).getPsiElement())){
          continue;
        }
      }
      if (Comparing.strEqual(problemDescriptor.getDescriptionTemplate(), descriptor.getDescriptionTemplate())){
        return true;
      }
    }
    return false;
  }


  public FileStatus getElementStatus(final RefElement element) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements != null){
        final boolean old = contains(element, myOldProblemElements.keySet());
        final boolean current = contains(element, getProblemElements().keySet());
        return calcStatus(old, current);
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  private HashMap<RefEntity, CommonProblemDescriptor[]> getProblemElements() {
    if (myProblemElements == null) {
      myProblemElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
    }
    return myProblemElements;
  }

  private HashMap<CommonProblemDescriptor, RefEntity> getProblemToElements() {
    if (myProblemToElements == null) {
      myProblemToElements = new HashMap<CommonProblemDescriptor, RefEntity>();
    }
    return myProblemToElements;
  }

  private HashMap<RefEntity, Set<QuickFix>> getQuickFixActions() {
    if (myQuickFixActions == null) {
      myQuickFixActions = new HashMap<RefEntity, Set<QuickFix>>();
    }
    return myQuickFixActions;
  }

  private HashMap<RefEntity, CommonProblemDescriptor[]> getIgnoredElements() {
    if (myIgnoredElements == null) {
      myIgnoredElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
    }
    return myIgnoredElements;
  }
}
