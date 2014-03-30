/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ToolExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtilBase;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class UnusedDeclarationInspection extends GlobalInspectionTool {
  public boolean ADD_MAINS_TO_ENTRIES = true;

  public boolean ADD_APPLET_TO_ENTRIES = true;
  public boolean ADD_SERVLET_TO_ENTRIES = true;
  public boolean ADD_NONJAVA_TO_ENTRIES = true;

  private Set<RefElement> myProcessedSuspicious = null;
  private int myPhase;
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.dead.code.display.name");
  @NonNls public static final String SHORT_NAME = "UnusedDeclaration";
  @NonNls private static final String ALTERNATIVE_ID = "unused";

  public final EntryPoint[] myExtensions;
  private static final Logger LOG = Logger.getInstance("#" + UnusedDeclarationInspection.class.getName());
  private GlobalInspectionContext myContext;

  public UnusedDeclarationInspection() {
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.DEAD_CODE_TOOL);
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
      @Override
      public int compare(final EntryPoint o1, final EntryPoint o2) {
        return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
      }
    });
    myExtensions = deadCodeAddins;
  }

  private GlobalInspectionContext getContext() {
    return myContext;
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
      gc.insets = new Insets(0, 20, 2, 0);
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myMainsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option"));
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_MAINS_TO_ENTRIES = myMainsCheckbox.isSelected();
        }
      });

      gc.gridy = 0;
      add(myMainsCheckbox, gc);

      myAppletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option3"));
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_APPLET_TO_ENTRIES = myAppletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myAppletToEntries, gc);

      myServletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option4"));
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.addActionListener(new ActionListener(){
        @Override
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
            @Override
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
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_NONJAVA_TO_ENTRIES = myNonJavaCheckbox.isSelected();
        }
      });

      gc.gridy++;
      add(myNonJavaCheckbox, gc);

      Project project = guessProject();
      JButton configureAnnotations = EntryPointsManager.getInstance(project).createConfigureAnnotationsBtn();
      gc.fill = GridBagConstraints.NONE;
      gc.gridy++;
      gc.insets.top = 10;
      gc.weighty = 1;

      add(configureAnnotations, gc);
    }

  }

  private Project guessProject() {
    Project project = myContext == null ? null : myContext.getProject();

    if (project == null) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      project = openProjects.length == 0 ? ProjectManager.getInstance().getDefaultProject() : openProjects[0];
    }
    return project;
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel scrollPane = new JPanel(new BorderLayout());
    scrollPane.add(new JLabel("Entry points:"), BorderLayout.NORTH);
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

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (EntryPoint extension : myExtensions) {
      extension.readExternal(node);
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    for (EntryPoint extension : myExtensions) {
      extension.writeExternal(node);
    }
  }

  private static boolean isExternalizableNoParameterConstructor(@NotNull PsiMethod method, RefClass refClass) {
    if (!method.isConstructor()) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) return false;
    final PsiClass aClass = method.getContainingClass();
    return aClass == null || isExternalizable(aClass, refClass);
  }

  private static boolean isSerializationImplicitlyUsedField(@NotNull PsiField field) {
    @NonNls final String name = field.getName();
    if (!HighlightUtilBase.SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !"serialPersistentFields".equals(name)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass, null);
  }

  private static boolean isWriteObjectMethod(@NotNull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"writeObject".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectOutputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isReadObjectMethod(@NotNull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"readObject".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectInputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isWriteReplaceMethod(@NotNull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"writeReplace".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!method.getReturnType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isReadResolveMethod(@NotNull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"readResolve".equals(name)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (!method.getReturnType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    final PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isSerializable(PsiClass aClass, @Nullable RefClass refClass) {
    final PsiClass serializableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io.Serializable", aClass.getResolveScope());
    return serializableClass != null && isSerializable(aClass, refClass, serializableClass);
  }

  private static boolean isExternalizable(@NotNull PsiClass aClass, RefClass refClass) {
    final GlobalSearchScope scope = aClass.getResolveScope();
    final PsiClass externalizableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io.Externalizable", scope);
    return externalizableClass != null && isSerializable(aClass, refClass, externalizableClass);
  }

  private static boolean isSerializable(PsiClass aClass, RefClass refClass, PsiClass serializableClass) {
    if (aClass == null) return false;
    if (aClass.isInheritor(serializableClass, true)) return true;
    if (refClass != null) {
      final Set<RefClass> subClasses = refClass.getSubClasses();
      for (RefClass subClass : subClasses) {
        if (isSerializable(subClass.getElement(), subClass, serializableClass)) return true;
      }
    }
    return false;
  }

  @Override
  public void runInspection(@NotNull final AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull final GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull final RefEntity refEntity) {
        if (refEntity instanceof RefJavaElement) {
          final RefElementImpl refElement = (RefElementImpl)refEntity;
          if (!refElement.isSuspicious()) return;

          PsiFile file = refElement.getContainingFile();

          if (file == null) return;
          final boolean isSuppressed = refElement.isSuppressed(getShortName(), ALTERNATIVE_ID);
          if (isSuppressed || !((GlobalInspectionContextBase)globalContext).isToCheckFile(file, UnusedDeclarationInspection.this)) {
            if (isSuppressed || !scope.contains(file)) {
              getEntryPointsManager().addEntryPoint(refElement, false);
            }
            return;
          }

          refElement.accept(new RefJavaVisitor() {
            @Override
            public void visitMethod(@NotNull RefMethod method) {
              if (isAddMainsEnabled() && method.isAppMain()) {
                getEntryPointsManager().addEntryPoint(method, false);
              }
            }

            @Override
            public void visitClass(@NotNull RefClass aClass) {
              if (isAddAppletEnabled() && aClass.isApplet() ||
                  isAddServletEnabled() && aClass.isServlet()) {
                getEntryPointsManager().addEntryPoint(aClass, false);
              }
            }
          });
        }
      }
    });

    if (isAddNonJavaUsedEnabled()) {
      checkForReachables(globalContext);
      final StrictUnreferencedFilter strictUnreferencedFilter = new StrictUnreferencedFilter(this, globalContext);
      ProgressManager.getInstance().runProcess(new Runnable() {
        @Override
        public void run() {
          final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(globalContext.getRefManager().getProject());
          globalContext.getRefManager().iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@NotNull final RefEntity refEntity) {
              if (refEntity instanceof RefClass && strictUnreferencedFilter.accepts((RefClass)refEntity)) {
                findExternalClassReferences((RefClass)refEntity);
              }
              else if (refEntity instanceof RefMethod) {
                RefMethod refMethod = (RefMethod)refEntity;
                if (refMethod.isConstructor() && strictUnreferencedFilter.accepts(refMethod)) {
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
                                                     @Override
                                                     public boolean process(PsiFile file, int startOffset, int endOffset) {
                                                       getEntryPointsManager().addEntryPoint(refElement, false);
                                                       return false;
                                                     }
                                                   },
                                                   GlobalSearchScope.projectScope(globalContext.getProject()));
              }
            }
          });
        }
      }, null);
    }

    myProcessedSuspicious = new HashSet<RefElement>();
    myPhase = 1;
  }

  public boolean isEntryPoint(@NotNull RefElement owner) {
    final PsiElement element = owner.getElement();
    if (RefUtil.isImplicitUsage(element)) return true;
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(element.getProject());
      if (entryPointsManager.isEntryPoint(element)) {
        return true;
      }
    }
    if (element != null) {
      for (EntryPoint extension : myExtensions) {
        if (extension.isEntryPoint(owner, element)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isEntryPoint(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    if (element instanceof PsiMethod && isAddMainsEnabled() && PsiClassImplUtil.isMainOrPremainMethod((PsiMethod)element)) {
      return true;
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      /*
      if (aClass.isAnnotationType()) {
        return true;
      }

      if (aClass.isEnum()) {
        return true;
      }
      */
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
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(project);
      if (entryPointsManager.isEntryPoint(element)) return true;
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
    private StrictUnreferencedFilter(@NotNull UnusedDeclarationInspection tool, @NotNull GlobalInspectionContext context) {
      super(tool, context);
    }

    @Override
    public int getElementProblemCount(@NotNull RefJavaElement refElement) {
      final int problemCount = super.getElementProblemCount(refElement);
      if (problemCount > -1) return problemCount;
      return refElement.isReferenced() ? 0 : 1;
    }
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager,
                                             @NotNull GlobalInspectionContext globalContext,
                                             @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    checkForReachables(globalContext);
    final RefFilter filter = myPhase == 1 ? new StrictUnreferencedFilter(this, globalContext) :
                             new RefUnreachableFilter(this, globalContext);
    final boolean[] requestAdded = {false};

    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;
        if (refEntity instanceof RefClass && ((RefClass)refEntity).isAnonymous()) return;
        RefJavaElement refElement = (RefJavaElement)refEntity;
        if (filter.accepts(refElement) && !myProcessedSuspicious.contains(refElement)) {
          refEntity.accept(new RefJavaVisitor() {
            @Override
            public void visitField(@NotNull final RefField refField) {
              myProcessedSuspicious.add(refField);
              PsiField psiField = refField.getElement();
              if (psiField != null && isSerializationImplicitlyUsedField(psiField)) {
                getEntryPointsManager().addEntryPoint(refField, false);
              }
              else {
                getJavaContext().enqueueFieldUsagesProcessor(refField, new GlobalJavaInspectionContext.UsagesProcessor() {
                  @Override
                  public boolean process(PsiReference psiReference) {
                    getEntryPointsManager().addEntryPoint(refField, false);
                    return false;
                  }
                });
                requestAdded[0] = true;
              }
            }

            @Override
            public void visitMethod(@NotNull final RefMethod refMethod) {
              myProcessedSuspicious.add(refMethod);
              if (refMethod instanceof RefImplicitConstructor) {
                visitClass(refMethod.getOwnerClass());
              }
              else {
                PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
                if (psiMethod != null && isSerializablePatternMethod(psiMethod, refMethod.getOwnerClass())) {
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

            @Override
            public void visitClass(@NotNull final RefClass refClass) {
              myProcessedSuspicious.add(refClass);
              if (!refClass.isAnonymous()) {
                getJavaContext().enqueueDerivedClassesProcessor(refClass, new GlobalJavaInspectionContext.DerivedClassesProcessor() {
                  @Override
                  public boolean process(PsiClass inheritor) {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                });

                getJavaContext().enqueueClassUsagesProcessor(refClass, new GlobalJavaInspectionContext.UsagesProcessor() {
                  @Override
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

  private static boolean isSerializablePatternMethod(@NotNull PsiMethod psiMethod, RefClass refClass) {
    return isReadObjectMethod(psiMethod, refClass) || isWriteObjectMethod(psiMethod, refClass) || isReadResolveMethod(psiMethod, refClass) ||
           isWriteReplaceMethod(psiMethod, refClass) || isExternalizableNoParameterConstructor(psiMethod, refClass);
  }

  private void enqueueMethodUsages(final RefMethod refMethod) {
    if (refMethod.getSuperMethods().isEmpty()) {
      getJavaContext().enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
        @Override
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

  private GlobalJavaInspectionContext getJavaContext() {
    return getContext().getExtension(GlobalJavaInspectionContext.CONTEXT);
  }

  @Nullable
  @Override
  public JobDescriptor[] getAdditionalJobs() {
    return new JobDescriptor[]{getContext().getStdJobDescriptors().BUILD_GRAPH, getContext().getStdJobDescriptors().FIND_EXTERNAL_USAGES};
  }


  void checkForReachables(@NotNull final GlobalInspectionContext context) {
    CodeScanner codeScanner = new CodeScanner();

    // Cleanup previous reachability information.
    context.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefJavaElement) {
          final RefJavaElementImpl refElement = (RefJavaElementImpl)refEntity;
          if (!((GlobalInspectionContextBase)context).isToCheckMember(refElement, UnusedDeclarationInspection.this)) return;
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

    @Override public void visitMethod(@NotNull RefMethod method) {
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

    @Override public void visitClass(@NotNull RefClass refClass) {
      boolean alreadyActive = refClass.isReachable();
      ((RefClassImpl)refClass).setReachable(true);

      if (!alreadyActive) {
        // Process class's static intitializers.
        makeClassInitializersReachable(refClass);
      }

      addInstantiatedClass(refClass);
    }

    @Override public void visitField(@NotNull RefField field) {
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

  @Override
  public void initialize(@NotNull GlobalInspectionContext context) {
    super.initialize(context);
    myContext = context;
  }

  @Override
  public void cleanup(Project project) {
    super.cleanup(project);
    myContext = null;
  }

  @Override
  public boolean isGraphNeeded() {
    return true;
  }
}
