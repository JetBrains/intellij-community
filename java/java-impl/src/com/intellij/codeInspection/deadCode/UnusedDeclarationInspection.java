/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 12, 2001
 * Time: 9:40:45 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */

package com.intellij.codeInspection.deadCode;

import com.intellij.ExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.EntryPointsNode;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class UnusedDeclarationInspection extends FilteringInspectionTool {
  public boolean ADD_MAINS_TO_ENTRIES = true;

  public boolean ADD_APPLET_TO_ENTRIES = true;
  public boolean ADD_SERVLET_TO_ENTRIES = true;
  public boolean ADD_NONJAVA_TO_ENTRIES = true;

  public JDOMExternalizableStringList ADDITIONAL_ANNOTATIONS = new JDOMExternalizableStringList();
  @NonNls private static final String[] ADDITIONAL_ANNOS = {
    "javax.ws.rs.*"
  };

  private HashSet<RefElement> myProcessedSuspicious = null;
  private int myPhase;
  private final QuickFixAction[] myQuickFixActions;
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.dead.code.display.name");
  private WeakUnreferencedFilter myFilter;
  private DeadHTMLComposer myComposer;
  @NonNls public static final String SHORT_NAME = "UnusedDeclaration";

  private static final String COMMENT_OUT_QUICK_FIX = InspectionsBundle.message("inspection.dead.code.comment.quickfix");
  private static final String DELETE_QUICK_FIX = InspectionsBundle.message("inspection.dead.code.safe.delete.quickfix");

  @NonNls private static final String DELETE = "delete";
  @NonNls private static final String COMMENT = "comment";
  @NonNls private static final String [] HINTS = {COMMENT, DELETE};

  public final EntryPoint[] myExtensions;
  private static final Logger LOG = Logger.getInstance("#" + UnusedDeclarationInspection.class.getName());

  public UnusedDeclarationInspection() {
    ContainerUtil.addAll(ADDITIONAL_ANNOTATIONS, ADDITIONAL_ANNOS);
    myQuickFixActions = new QuickFixAction[]{new PermanentDeleteAction(), new CommentOutBin(), new MoveToEntries()};
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    final EntryPoint[] deadCodeAddins = new EntryPoint[point.getExtensions().length];
    EntryPoint[] extensions = point.getExtensions();
    for (int i = 0, extensionsLength = extensions.length; i < extensionsLength; i++) {
      EntryPoint entryPoint = extensions[i];
      try {
        deadCodeAddins[i] = entryPoint.clone();
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
      }
    }
    Arrays.sort(deadCodeAddins, new Comparator<EntryPoint>() {
      public int compare(final EntryPoint o1, final EntryPoint o2) {
        return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
      }
    });
    myExtensions = deadCodeAddins;
  }

  public void initialize(@NotNull final GlobalInspectionContextImpl context) {
    super.initialize(context);
    ((EntryPointsManagerImpl)getEntryPointsManager()).setAddNonJavaEntries(ADD_NONJAVA_TO_ENTRIES);
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myMainsCheckbox;
    private final JCheckBox myAppletToEntries;
    private final JCheckBox myServletToEntries;
    private final JCheckBox myNonJavaCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myMainsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option"));
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ADD_MAINS_TO_ENTRIES = myMainsCheckbox.isSelected();
        }
      });

      gc.gridy = 0;
      add(myMainsCheckbox, gc);

      myAppletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option3"));
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ADD_APPLET_TO_ENTRIES = myAppletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myAppletToEntries, gc);

      myServletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option4"));
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          ADD_SERVLET_TO_ENTRIES = myServletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myServletToEntries, gc);

      for (final EntryPoint extension : myExtensions) {
        if (extension.showUI()) {
          final JCheckBox extCheckbox = new JCheckBox(extension.getDisplayName());
          extCheckbox.setSelected(extension.isSelected());
          extCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              extension.setSelected(extCheckbox.isSelected());
            }
          });
          gc.gridy++;
          add(extCheckbox, gc);
        }
      }

      myNonJavaCheckbox =
      new JCheckBox(InspectionsBundle.message("inspection.dead.code.option5"));
      myNonJavaCheckbox.setSelected(ADD_NONJAVA_TO_ENTRIES);
      myNonJavaCheckbox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ADD_NONJAVA_TO_ENTRIES = myNonJavaCheckbox.isSelected();
        }
      });

      gc.gridy++;
      add(myNonJavaCheckbox, gc);

      final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(ADDITIONAL_ANNOTATIONS, InspectionsBundle.message(
        "inspections.dead.code.entry.points.annotations.list.title"));
      gc.gridy++;
      gc.weighty = 1;
      add(listPanel, gc);
    }
  }

  public JComponent createOptionsPanel() {
    final JPanel scrollPane = new JPanel(new BorderLayout());
    scrollPane.add(SeparatorFactory.createSeparator("Entry points", null), BorderLayout.NORTH);
    scrollPane.add(new OptionsPanel(), BorderLayout.CENTER);
    return scrollPane;
  }

  private boolean isAddMainsEnabled() {
    return ADD_MAINS_TO_ENTRIES;
  }

  private boolean isAddAppletEnabled() {
    return ADD_APPLET_TO_ENTRIES;
  }

  private boolean isAddServletEnabled() {
    return ADD_SERVLET_TO_ENTRIES;
  }

  private boolean isAddNonJavaUsedEnabled() {
    return ADD_NONJAVA_TO_ENTRIES;
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  public void readSettings(Element node) throws InvalidDataException {
    super.readSettings(node);
    for (EntryPoint extension : myExtensions) {
      extension.readExternal(node);
    }
  }

  public void writeSettings(Element node) throws WriteExternalException {
    super.writeSettings(node);
    for (EntryPoint extension : myExtensions) {
      extension.writeExternal(node);
    }
  }

  private static boolean isSerializationImplicitlyUsedField(PsiField field) {
    @NonNls final String name = field.getName();
    if (!"serialVersionUID".equals(name) && !"serialPersistentFields".equals(name)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass);
  }

  private static boolean isWriteObjectMethod(PsiMethod method) {
    @NonNls final String name = method.getName();
    if (!"writeObject".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectOutputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass));
  }

  private static boolean isReadObjectMethod(PsiMethod method) {
    @NonNls final String name = method.getName();
    if (!"readObject".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectInputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass));
  }

  private static boolean isWriteReplaceMethod(PsiMethod method) {
    @NonNls final String name = method.getName();
    if (!"writeReplace".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!method.getReturnType().equalsToText("java.lang.Object")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass));
  }

  private static boolean isReadResolveMethod(PsiMethod method) {
    @NonNls final String name = method.getName();
    if (!"readResolve".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!method.getReturnType().equalsToText("java.lang.Object")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass));
  }

  private static boolean isSerializable(PsiClass aClass) {
    PsiClass serializableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io.Serializable", aClass.getResolveScope());
    return serializableClass != null && aClass.isInheritor(serializableClass, true);
  }

  public void runInspection(final AnalysisScope scope, final InspectionManager manager) {
    getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(final RefEntity refEntity) {
        if (refEntity instanceof RefJavaElement) {
          final RefElementImpl refElement = (RefElementImpl)refEntity;
          final PsiElement element = refElement.getElement();
          if (element == null) return;
          final boolean isSuppressed = refElement.isSuppressed(getShortName());
          if (!getContext().isToCheckMember(element, UnusedDeclarationInspection.this) || isSuppressed) {
            if (isSuppressed || !scope.contains(element)) {
              getEntryPointsManager().addEntryPoint(refElement, false);
            }
            return;
          }
          if (!refElement.isSuspicious()) return;
          refElement.accept(new RefJavaVisitor() {
            @Override public void visitElement(final RefEntity elem) {
              if (elem instanceof RefElement) {
                final RefElement element = (RefElement)elem;
                if (isEntryPoint(element)) {
                  getEntryPointsManager().addEntryPoint(element, false);
                }
              }
            }

            @Override public void visitMethod(RefMethod method) {
              if (isAddMainsEnabled() && method.isAppMain()) {
                getEntryPointsManager().addEntryPoint(method, false);
              } else {
                super.visitMethod(method);
              }
            }

            @Override public void visitClass(RefClass aClass) {
              final PsiClass psiClass = aClass.getElement();
              if (
                isAddAppletEnabled() && aClass.isApplet() ||
                isAddServletEnabled() && aClass.isServlet()) {
                getEntryPointsManager().addEntryPoint(aClass, false);
              } else if (psiClass.isAnnotationType()){
                getEntryPointsManager().addEntryPoint(aClass, false);
                final PsiMethod[] psiMethods = psiClass.getMethods();
                for (PsiMethod psiMethod : psiMethods) {
                  getEntryPointsManager().addEntryPoint(getRefManager().getReference(psiMethod), false);
                }
              } else if (psiClass.isEnum()) {
                getEntryPointsManager().addEntryPoint(aClass, false);
              } else {
                super.visitClass(aClass);
              }
            }
          });
        }
      }
    });

    if (isAddNonJavaUsedEnabled()) {
      checkForReachables();
      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          final RefFilter filter = new StrictUnreferencedFilter(UnusedDeclarationInspection.this);
          final PsiSearchHelper helper = PsiManager.getInstance(getRefManager().getProject()).getSearchHelper();
          getRefManager().iterate(new RefJavaVisitor() {
            @Override public void visitElement(final RefEntity refEntity) {
              if (refEntity instanceof RefClass && filter.accepts((RefClass)refEntity)) {
                findExternalClassReferences((RefClass)refEntity);
              }
              else if (refEntity instanceof RefMethod) {
                RefMethod refMethod = (RefMethod)refEntity;
                if (refMethod.isConstructor() && filter.accepts(refMethod)) {
                  findExternalClassReferences(refMethod.getOwnerClass());
                }
              }
            }

            private void findExternalClassReferences(final RefClass refElement) {
              PsiClass psiClass = refElement.getElement();
              String qualifiedName = psiClass.getQualifiedName();
              if (qualifiedName != null) {
                helper.processUsagesInNonJavaFiles(qualifiedName,
                                                   new PsiNonJavaFileReferenceProcessor() {
                                                     public boolean process(PsiFile file, int startOffset, int endOffset) {
                                                       getEntryPointsManager().addEntryPoint(refElement, false);
                                                       return false;
                                                     }
                                                   },
                                                   GlobalSearchScope.projectScope(getContext().getProject()));
              }
            }
          });
        }
      }, null);
    }

    myProcessedSuspicious = new HashSet<RefElement>();
    myPhase = 1;
  }

  private boolean isEntryPoint(final RefElement owner) {
    if (RefUtil.isEntryPoint(owner)) return true;
    final PsiElement element = owner.getElement();
    if (element instanceof PsiModifierListOwner
        && AnnotationUtil.isAnnotated((PsiModifierListOwner)element, ADDITIONAL_ANNOTATIONS)) {
      return true;
    }
    for (EntryPoint extension : myExtensions) {
      if (extension.isEntryPoint(owner, element)) {
        return true;
      }
    }
    return false;
  }

  public boolean isEntryPoint(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    if (element instanceof PsiMethod && isAddMainsEnabled() && PsiClassImplUtil.isMainMethod((PsiMethod)element)) {
      return true;
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      if (aClass.isAnnotationType()) {
        return true;
      }

      if (aClass.isEnum()) {
        return true;
      }
      final PsiClass applet = psiFacade.findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
      if (isAddAppletEnabled() && applet != null && aClass.isInheritor(applet, true)) {
        return true;
      }

      final PsiClass servlet = psiFacade.findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
      if (isAddServletEnabled() && servlet != null && aClass.isInheritor(servlet, true)) {
        return true;
      }
      if (isAddMainsEnabled() && PsiMethodUtil.hasMainMethod(aClass)) return true;
    }
    if (element instanceof PsiModifierListOwner
        && AnnotationUtil.checkAnnotatedUsingPatterns((PsiModifierListOwner)element, ADDITIONAL_ANNOTATIONS)) {
      return true;
    }
    for (EntryPoint extension : myExtensions) {
      if (extension.isEntryPoint(element)) {
        return true;
      }
    }
    final ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
    for (ImplicitUsageProvider provider : implicitUsageProviders) {
      if (provider.isImplicitUsage(element)) return true;
    }
    return false;
  }

  private static class StrictUnreferencedFilter extends UnreferencedFilter {
    private StrictUnreferencedFilter(final InspectionTool tool) {
      super(tool);
    }

    public int getElementProblemCount(RefJavaElement refElement) {
      final int problemCount = super.getElementProblemCount(refElement);
      if (problemCount > -1) return problemCount;
      return refElement.isReferenced() ? 0 : 1;
    }
  }

  private static class WeakUnreferencedFilter extends UnreferencedFilter {
    private WeakUnreferencedFilter(final InspectionTool tool) {
      super(tool);
    }

    public int getElementProblemCount(final RefJavaElement refElement) {
      final int problemCount = super.getElementProblemCount(refElement);
      if (problemCount > - 1) return problemCount;
      if (!((RefElementImpl)refElement).hasSuspiciousCallers() || ((RefJavaElementImpl)refElement).isSuspiciousRecursive()) return 1;
      return 0;
    }
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    checkForReachables();
    final RefFilter filter = myPhase == 1 ? new StrictUnreferencedFilter(this) : new RefUnreachableFilter(this);
    final boolean[] requestAdded = {false};

    getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;
        if (refEntity instanceof RefClass && ((RefClass)refEntity).isAnonymous()) return;
        RefJavaElement refElement= (RefJavaElement)refEntity;
        if (filter.accepts(refElement) && !myProcessedSuspicious.contains(refElement)) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitField(final RefField refField) {
              myProcessedSuspicious.add(refField);
              PsiField psiField = refField.getElement();
              if (isSerializationImplicitlyUsedField(psiField)) {
                getEntryPointsManager().addEntryPoint(refField, false);
              }
              else {
                getJavaContext().enqueueFieldUsagesProcessor(refField, new GlobalJavaInspectionContext.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    getEntryPointsManager().addEntryPoint(refField, false);
                    return false;
                  }
                });
                requestAdded[0] = true;
              }
            }

            @Override public void visitMethod(final RefMethod refMethod) {
              myProcessedSuspicious.add(refMethod);
              if (refMethod instanceof RefImplicitConstructor) {
                visitClass(refMethod.getOwnerClass());
              }
              else {
                PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
                if (isSerializablePatternMethod(psiMethod)) {
                  getEntryPointsManager().addEntryPoint(refMethod, false);
                }
                else if (!refMethod.isExternalOverride() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
                  for (final RefMethod derivedMethod : refMethod.getDerivedMethods()) {
                    myProcessedSuspicious.add(derivedMethod);
                  }

                  enqueueMethodUsages(refMethod);
                  requestAdded[0] = true;
                }
              }
            }

            @Override public void visitClass(final RefClass refClass) {
              myProcessedSuspicious.add(refClass);
              if (!refClass.isAnonymous()) {
                getJavaContext().enqueueDerivedClassesProcessor(refClass, new GlobalJavaInspectionContext.DerivedClassesProcessor() {
                  public boolean process(PsiClass inheritor) {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                });

                getJavaContext().enqueueClassUsagesProcessor(refClass, new GlobalJavaInspectionContext.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                });
                requestAdded[0] = true;
              }
            }
          });
        }
      }
    });

    if (!requestAdded[0]) {
      if (myPhase == 2) {
        myProcessedSuspicious = null;
        return false;
      }
      else {
        myPhase = 2;
      }
    }

    return true;
  }

  private static boolean isSerializablePatternMethod(PsiMethod psiMethod) {
    return isReadObjectMethod(psiMethod) || isWriteObjectMethod(psiMethod) || isReadResolveMethod(psiMethod) ||
           isWriteReplaceMethod(psiMethod);
  }

  private void enqueueMethodUsages(final RefMethod refMethod) {
    if (refMethod.getSuperMethods().isEmpty()) {
      getJavaContext().enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
        public boolean process(PsiReference psiReference) {
          getEntryPointsManager().addEntryPoint(refMethod, false);
          return false;
        }
      });
    }
    else {
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        enqueueMethodUsages(refSuper);
      }
    }
  }

  public GlobalJavaInspectionContext getJavaContext() {
    return getContext().getExtension(GlobalJavaInspectionContext.CONTEXT);
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new WeakUnreferencedFilter(this);
    }
    return myFilter;
  }

  public HTMLComposerImpl getComposer() {
    if (myComposer == null) {
      myComposer = new DeadHTMLComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    final WeakUnreferencedFilter filter = new WeakUnreferencedFilter(this);
    getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;
        if (!getIgnoredRefElements().contains(refEntity) && filter.accepts((RefJavaElement)refEntity)) {
          if (refEntity instanceof RefImplicitConstructor) refEntity = ((RefImplicitConstructor)refEntity).getOwnerClass();
          Element element = refEntity.getRefManager().export(refEntity, parentNode, -1);
          @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));

          if (refEntity instanceof RefElement) {
            final RefElement refElement = (RefElement)refEntity;
            final HighlightSeverity severity = getCurrentSeverity(refElement);
            final String attributeKey = getTextAttributeKey(refElement.getElement().getProject(), severity, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            problemClassElement.setAttribute("severity", severity.myName);
            problemClassElement.setAttribute("attribute_key", attributeKey);
          }

          problemClassElement.addContent(InspectionsBundle.message("inspection.export.results.dead.code"));
          element.addContent(problemClassElement);

          @NonNls Element hintsElement = new Element("hints");

          for (String hint : HINTS) {
            @NonNls Element hintElement = new Element("hint");
            hintElement.setAttribute("value", hint);
            hintsElement.addContent(hintElement);
          }
          element.addContent(hintsElement);


          Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
          StringBuffer buf = new StringBuffer();
          DeadHTMLComposer.appendProblemSynopsis((RefElement)refEntity, buf);
          descriptionElement.addContent(buf.toString());
          element.addContent(descriptionElement);
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    return myQuickFixActions;
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[]{GlobalInspectionContextImpl.BUILD_GRAPH, GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES};
  }

  private static void commentOutDead(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();

    if (psiFile != null) {
      Document doc = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(psiFile);
      if (doc != null) {
        TextRange textRange = psiElement.getTextRange();
        SimpleDateFormat format = new SimpleDateFormat();
        String date = format.format(new Date());

        int startOffset = textRange.getStartOffset();
        CharSequence chars = doc.getCharsSequence();
        while (CharArrayUtil.regionMatches(chars, startOffset, InspectionsBundle.message("inspection.dead.code.comment"))) {
          int line = doc.getLineNumber(startOffset) + 1;
          if (line < doc.getLineCount()) {
            startOffset = doc.getLineStartOffset(line);
            startOffset = CharArrayUtil.shiftForward(chars, startOffset, " \t");
          }
        }

        int endOffset = textRange.getEndOffset();

        int line1 = doc.getLineNumber(startOffset);
        int line2 = doc.getLineNumber(endOffset - 1);

        if (line1 == line2) {
          doc.insertString(startOffset, InspectionsBundle.message("inspection.dead.code.date.comment", date));
        }
        else {
          for (int i = line1; i <= line2; i++) {
            doc.insertString(doc.getLineStartOffset(i), "//");
          }

          doc.insertString(doc.getLineStartOffset(Math.min(line2 + 1, doc.getLineCount() - 1)),
                           InspectionsBundle.message("inspection.dead.code.stop.comment", date));
          doc.insertString(doc.getLineStartOffset(line1), InspectionsBundle.message("inspection.dead.code.start.comment", date));
        }
      }
    }
  }

  @Nullable
  public IntentionAction findQuickFixes(final CommonProblemDescriptor descriptor, final String hint) {
    if (descriptor instanceof ProblemDescriptor) {
      if (DELETE.equals(hint)) {
        return new PermanentDeleteFix(((ProblemDescriptor)descriptor).getPsiElement());
      } else if (COMMENT.equals(hint)) {
        return new CommentOutFix(((ProblemDescriptor)descriptor).getPsiElement());
      }
    }
    return null;
  }

  private class PermanentDeleteAction extends QuickFixAction {
    private PermanentDeleteAction() {
      super(DELETE_QUICK_FIX, IconLoader.getIcon("/actions/cancel.png"), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), UnusedDeclarationInspection.this);
    }

    protected boolean applyFix(final RefElement[] refElements) {
      if (!super.applyFix(refElements)) return false;
      final ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
      for (RefElement refElement : refElements) {
        PsiElement psiElement = refElement.getElement();
        if (psiElement == null) continue;
        if (getFilter().getElementProblemCount((RefJavaElement)refElement) == 0) continue;
        psiElements.add(psiElement);
      }

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final Project project = getContext().getProject();
          SafeDeleteHandler.invoke(project, psiElements.toArray(new PsiElement[psiElements.size()]), false, new Runnable(){
            public void run() {
              removeElements(refElements, project, UnusedDeclarationInspection.this);
            }
          });
        }
      });

      return false; //refresh after safe delete dialog is closed
    }
  }

  private static class PermanentDeleteFix implements IntentionAction {
    private final PsiElement myElement;

    private PermanentDeleteFix(final PsiElement element) {
      myElement = element;
    }

    @NotNull
    public String getText() {
      return DELETE_QUICK_FIX;
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (myElement != null && myElement.isValid()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            SafeDeleteHandler
              .invoke(myElement.getProject(), new PsiElement[]{PsiTreeUtil.getParentOfType(myElement, PsiModifierListOwner.class)}, false);
          }
        });
      }
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  private class CommentOutBin extends QuickFixAction {
    private CommentOutBin() {
      super(COMMENT_OUT_QUICK_FIX, null, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK),
            UnusedDeclarationInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      if (!super.applyFix(refElements)) return false;
      ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>(1);
      for (RefElement refElement : refElements) {
        PsiElement psiElement = refElement.getElement();
        if (psiElement == null) continue;
        if (getFilter().getElementProblemCount((RefJavaElement)refElement) == 0) continue;
        commentOutDead(psiElement);
        refElement.getRefManager().removeRefElement(refElement, deletedRefs);
      }

      EntryPointsManager entryPointsManager = getEntryPointsManager();
      for (RefElement refElement : deletedRefs) {
        entryPointsManager.removeEntryPoint(refElement);
      }

      return true;
    }
  }

  private static class CommentOutFix implements IntentionAction {
    private final PsiElement myElement;

    private CommentOutFix(final PsiElement element) {
      myElement = element;
    }

    @NotNull
    public String getText() {
      return COMMENT_OUT_QUICK_FIX;
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (myElement != null && myElement.isValid()) {
        commentOutDead(PsiTreeUtil.getParentOfType(myElement, PsiModifierListOwner.class));        
      }
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  private class MoveToEntries extends QuickFixAction {
    private MoveToEntries() {
      super(InspectionsBundle.message("inspection.dead.code.entry.point.quickfix"), null, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), UnusedDeclarationInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      final EntryPointsManager entryPointsManager = getEntryPointsManager();
      for (RefElement refElement : refElements) {
        entryPointsManager.addEntryPoint(refElement, true);
      }

      return true;
    }


  }

  private void checkForReachables() {
    CodeScanner codeScanner = new CodeScanner();

    // Cleanup previous reachability information.
    getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefJavaElement) {
          final RefJavaElementImpl refElement = (RefJavaElementImpl)refEntity;
          final PsiElement element = refElement.getElement();
          if (element == null) return;
          if (!getContext().isToCheckMember(refElement, UnusedDeclarationInspection.this)) return;
          refElement.setReachable(false);
        }
      }
    });


    for (RefElement entry : getEntryPointsManager().getEntryPoints()) {
      entry.accept(codeScanner);
    }

    while (codeScanner.newlyInstantiatedClassesCount() != 0) {
      codeScanner.cleanInstantiatedClassesCount();
      codeScanner.processDelayedMethods();
    }
  }

  private EntryPointsManager getEntryPointsManager() {
    return getContext().getExtension(GlobalJavaInspectionContext.CONTEXT).getEntryPointsManager(getContext().getRefManager());
  }

  private static class CodeScanner extends RefJavaVisitor {
    private final HashMap<RefClass, HashSet<RefMethod>> myClassIDtoMethods;
    private final HashSet<RefClass> myInstantiatedClasses;
    private int myInstantiatedClassesCount;
    private final HashSet<RefMethod> myProcessedMethods;

    private CodeScanner() {
      myClassIDtoMethods = new HashMap<RefClass, HashSet<RefMethod>>();
      myInstantiatedClasses = new HashSet<RefClass>();
      myProcessedMethods = new HashSet<RefMethod>();
      myInstantiatedClassesCount = 0;
    }

    @Override public void visitMethod(RefMethod method) {
      if (!myProcessedMethods.contains(method)) {
        // Process class's static intitializers
        if (method.isStatic() || method.isConstructor()) {
          if (method.isConstructor()) {
            addInstantiatedClass(method.getOwnerClass());
          }
          else {
            ((RefClassImpl)method.getOwnerClass()).setReachable(true);
          }
          myProcessedMethods.add(method);
          makeContentReachable((RefJavaElementImpl)method);
          makeClassInitializersReachable(method.getOwnerClass());
        }
        else {
          if (isClassInstantiated(method.getOwnerClass())) {
            myProcessedMethods.add(method);
            makeContentReachable((RefJavaElementImpl)method);
          }
          else {
            addDelayedMethod(method);
          }

          for (RefMethod refSub : method.getDerivedMethods()) {
            visitMethod(refSub);
          }
        }
      }
    }

    @Override public void visitClass(RefClass refClass) {
      boolean alreadyActive = refClass.isReachable();
      ((RefClassImpl)refClass).setReachable(true);

      if (!alreadyActive) {
        // Process class's static intitializers.
        makeClassInitializersReachable(refClass);
      }

      addInstantiatedClass(refClass);
    }

    @Override public void visitField(RefField field) {
      // Process class's static intitializers.
      if (!field.isReachable()) {
        makeContentReachable((RefJavaElementImpl)field);
        makeClassInitializersReachable(field.getOwnerClass());
      }
    }

    private void addInstantiatedClass(RefClass refClass) {
      if (myInstantiatedClasses.add(refClass)) {
        ((RefClassImpl)refClass).setReachable(true);
        myInstantiatedClassesCount++;

        final List<RefMethod> refMethods = refClass.getLibraryMethods();
        for (RefMethod refMethod : refMethods) {
          refMethod.accept(this);
        }
        for (RefClass baseClass : refClass.getBaseClasses()) {
          addInstantiatedClass(baseClass);
        }
      }
    }

    private void makeContentReachable(RefJavaElementImpl refElement) {
      refElement.setReachable(true);
      for (RefElement refCallee : refElement.getOutReferences()) {
        refCallee.accept(this);
      }
    }

    private void makeClassInitializersReachable(RefClass refClass) {
      for (RefElement refCallee : refClass.getOutReferences()) {
        refCallee.accept(this);
      }
    }

    private void addDelayedMethod(RefMethod refMethod) {
      HashSet<RefMethod> methods = myClassIDtoMethods.get(refMethod.getOwnerClass());
      if (methods == null) {
        methods = new HashSet<RefMethod>();
        myClassIDtoMethods.put(refMethod.getOwnerClass(), methods);
      }
      methods.add(refMethod);
    }

    private boolean isClassInstantiated(RefClass refClass) {
      return myInstantiatedClasses.contains(refClass);
    }

    private int newlyInstantiatedClassesCount() {
      return myInstantiatedClassesCount;
    }

    private void cleanInstantiatedClassesCount() {
      myInstantiatedClassesCount = 0;
    }

    private void processDelayedMethods() {
      RefClass[] instClasses = myInstantiatedClasses.toArray(new RefClass[myInstantiatedClasses.size()]);
      for (RefClass refClass : instClasses) {
        if (isClassInstantiated(refClass)) {
          HashSet<RefMethod> methods = myClassIDtoMethods.get(refClass);
          if (methods != null) {
            RefMethod[] arMethods = methods.toArray(new RefMethod[methods.size()]);
            for (RefMethod arMethod : arMethods) {
              arMethod.accept(this);
            }
          }
        }
      }
    }
  }

  public void updateContent() {
    checkForReachables();
    super.updateContent();
  }

  public InspectionNode createToolNode(final InspectionRVContentProvider provider, final InspectionTreeNode parentNode, final boolean showStructure) {
    final InspectionNode toolNode = super.createToolNode(provider, parentNode, showStructure);
    final EntryPointsNode entryPointsNode = new EntryPointsNode(this);
    provider.appendToolNodeContent(entryPointsNode, toolNode, showStructure);
    return entryPointsNode;
  }
}
