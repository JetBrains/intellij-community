// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public final class TestIntegrationUtils {
  private static final Logger LOG = Logger.getInstance(TestIntegrationUtils.class);

  public enum MethodKind {
    SET_UP("setUp") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getSetUpMethodFileTemplateDescriptor();
      }
    },
    BEFORE_CLASS("beforeClass") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getBeforeClassMethodFileTemplateDescriptor();
      }
    },
    TEAR_DOWN("tearDown") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getTearDownMethodFileTemplateDescriptor();
      }
    },
    AFTER_CLASS("afterClass") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getAfterClassMethodFileTemplateDescriptor();
      }
    },
    TEST("test") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getTestMethodFileTemplateDescriptor();
      }
    },
    DATA("data") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        if (framework instanceof JavaTestFramework) {
          return ((JavaTestFramework)framework).getParametersMethodFileTemplateDescriptor();
        }
        return null;
      }
    },
    TEST_CLASS("testClass") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        if (framework instanceof JavaTestFramework) {
          return ((JavaTestFramework)framework).getTestClassFileTemplateDescriptor();
        }
        return null;
      }
    };
    private final @NotNull String myDefaultName;

    MethodKind(@NotNull String defaultName) {
      myDefaultName = defaultName;
    }

    public @NotNull String getDefaultName() {
      return myDefaultName;
    }

    public abstract FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework);
  }

  public static boolean isTest(@NotNull PsiElement element) {
    PsiClass klass = findOuterClass(element);
    return klass != null && TestFrameworks.getInstance().isTestClass(klass);
  }

  public static @Nullable PsiClass findOuterClass(@NotNull PsiElement element) {
    PsiClass result = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (result == null) {
       final PsiFile containingFile = element.getContainingFile();
       if (containingFile instanceof PsiClassOwner){
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) {
          result = classes[0];
        }
      }
    }
    if (result == null) return null;
    do {
      PsiClass nextParent = PsiTreeUtil.getParentOfType(result, PsiClass.class, true);
      if (nextParent == null) return result;
      result = nextParent;
    }
    while (true);
  }

  public static List<MemberInfo> extractClassMethods(PsiClass clazz, boolean includeInherited) {
    List<MemberInfo> result = new ArrayList<>();
    Set<PsiClass> classes;
    if (includeInherited) {
      classes = InheritanceUtil.getSuperClasses(clazz);
      classes.add(clazz);
    }
    else {
      classes = Collections.singleton(clazz);
    }
    for (PsiClass aClass : classes) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) continue;
      MemberInfo.extractClassMembers(aClass, result, new MemberInfo.Filter<>() {
        @Override
        public boolean includeMember(PsiMember member) {
          if (!(member instanceof PsiMethod)) return false;
          if (member.hasModifierProperty(PsiModifier.PRIVATE) ||
              (member.hasModifierProperty(PsiModifier.ABSTRACT) && member.getContainingClass() != clazz)) {
            return false;
          }
          return true;
        }
      }, false);
    }

    return result;
  }

  public static void runTestMethodTemplate(@NotNull MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiClass targetClass,
                                           final PsiMethod method,
                                           @Nullable String name,
                                           boolean automatic, Set<? super String> existingNames) {
    runTestMethodTemplate(methodKind, framework, editor, targetClass, null, method, name, automatic, existingNames);
  }

  public static void runTestMethodTemplate(@NotNull MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiClass targetClass,
                                           @Nullable PsiClass sourceClass,
                                           final PsiMethod method,
                                           @Nullable String name,
                                           boolean automatic,
                                           Set<? super String> existingNames) {
    runTestMethodTemplate(editor, targetClass, method, automatic,
                          createTestMethodTemplate(methodKind, framework, targetClass, sourceClass, name, automatic, existingNames));
  }

  public static void runTestMethodTemplate(final Editor editor,
                                           final PsiClass targetClass,
                                           final PsiMethod method,
                                           boolean automatic, final Template template) {
    runTestMethodTemplate(editor, targetClass, method, method.getModifierList(), automatic, template);
  }

  public static void runTestMethodTemplate(@NotNull MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiElement targetClass,
                                           @Nullable PsiClass sourceClass,
                                           final PsiElement method,
                                           final PsiElement methodModifierList,
                                           @Nullable String name,
                                           boolean automatic,
                                           Set<? super String> existingNames) {
    runTestMethodTemplate(editor, targetClass, method, methodModifierList, automatic,
                          createTestMethodTemplate(methodKind, framework, targetClass, sourceClass, name, automatic, existingNames));
  }

  public static void runTestMethodTemplate(final Editor editor,
                                           final PsiElement targetClass,
                                           final PsiElement method,
                                           final PsiElement methodModifierList,
                                           boolean automatic,
                                           final Template template) {
    final int startOffset = methodModifierList.getTextRange().getStartOffset();
    final TextRange range = new TextRange(startOffset, method.getTextRange().getEndOffset());
    editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
    editor.getCaretModel().moveToOffset(range.getStartOffset());

    final Project project = targetClass.getProject();

    TemplateEditingAdapter adapter = null;

    if (!automatic) {
      adapter = new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            PsiElement el = PsiTreeUtil.findElementOfClassAtOffset(psi, editor.getCaretModel().getOffset() - 1, PsiMethod.class, false);

            if (el != null) {
              PsiMethod method1 = PsiTreeUtil.getParentOfType(el, PsiMethod.class, false);
              if (method1 != null) {
                if (method1.findDeepestSuperMethods().length > 0) {
                  GenerateMembersUtil.setupGeneratedMethod(method1);
                }
                CreateFromUsageUtils.setupEditor(method1, editor);
              }
            }
          });
        }
      };
    }

    TemplateManager.getInstance(project).startTemplate(editor, template, adapter);
  }

  public static Template createTestMethodTemplate(@NotNull MethodKind methodKind,
                                                  TestFramework descriptor,
                                                  @NotNull PsiClass targetClass,
                                                  @Nullable String name,
                                                  boolean automatic,
                                                  Set<? super String> existingNames) {
    return createTestMethodTemplate(methodKind, descriptor, targetClass, null, name, automatic, existingNames);
  }

  public static Template createTestMethodTemplate(@NotNull MethodKind methodKind,
                                                  TestFramework descriptor,
                                                  @NotNull PsiClass targetClass,
                                                  @Nullable PsiClass sourceClass,
                                                  @Nullable String name,
                                                  boolean automatic,
                                                  Set<? super String> existingNames) {
    return createTestMethodTemplate(methodKind, descriptor, (PsiElement) targetClass, sourceClass, name, automatic, existingNames);
  }

  public static Template createTestMethodTemplate(@NotNull MethodKind methodKind,
                                                  TestFramework descriptor,
                                                  @NotNull PsiElement targetClass,
                                                  @Nullable PsiClass sourceClass,
                                                  @Nullable String name,
                                                  boolean automatic,
                                                  Set<? super String> existingNames) {
    FileTemplateDescriptor templateDesc = methodKind.getFileTemplateDescriptor(descriptor);
    String templateName = templateDesc.getFileName();
    Project project = targetClass.getProject();
    FileTemplate fileTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(templateName);
    Template template = TemplateManager.getInstance(project).createTemplate("", "");

    String templateText;
    try {
      Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();
      if (sourceClass != null && sourceClass.isValid()) {
        properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, sourceClass.getQualifiedName());
      }

      templateText = fileTemplate.getText(properties);
    }
    catch (IOException e) {
      LOG.warn(e);
      templateText = fileTemplate.getText();
    }

    if (name == null) name = methodKind.getDefaultName();

    if (existingNames != null && !existingNames.add(name)) {
      int idx = 1;
      while (existingNames.contains(name)) {
        if (!name.startsWith("test")) {
          name = "test" + StringUtil.capitalize(name);
          if (existingNames.add(name)) {
            break;
          }
        }
        String newName = name + (idx++);
        if (existingNames.add(newName)) {
          name = newName;
          break;
        }
      }
    }

    templateText = StringUtil.replace(templateText, "${BODY}\n", "");

    int from = 0;
    while (true) {
      int index = templateText.indexOf("${NAME}", from);
      if (index == -1) break;

      template.addTextSegment(templateText.substring(from, index));

      if (index > 0 && !Character.isWhitespace(templateText.charAt(index - 1))) {
        name = StringUtil.capitalize(name);
      }
      else {
        name = StringUtil.decapitalize(name);
      }
      if (from == 0) {
        Expression nameExpr = new ConstantNode(name);
        template.addVariable("name", nameExpr, nameExpr, !automatic);
      }
      else {
        template.addVariableSegment("name");
      }

      from = index + "${NAME}".length();
    }
    template.addTextSegment(templateText.substring(from));

    template.setToIndent(true);
    template.setToReformat(true);
    template.setToShortenLongNames(true);

    return template;
  }

  public static PsiMethod createDummyMethod(@NotNull PsiElement context) {
    JVMElementFactory factory = JVMElementFactories.getFactory(context.getLanguage(), context.getProject());
    if (factory == null) factory = JavaPsiFacade.getElementFactory(context.getProject());
    return factory.createMethod("dummy", PsiTypes.voidType());
  }

  public static List<TestFramework> findSuitableFrameworks(PsiClass targetClass) {
    List<TestFramework> frameworks = ContainerUtil.filter(TestFramework.EXTENSION_NAME.getExtensionList(), framework ->
      TestFrameworks.isSuitableByLanguage(targetClass, framework)
    );
    Project project = targetClass.getProject();

    List<TestFramework> result = new SmartList<>();

    for (TestFramework framework : frameworks) {
      if (isAvailableFor(project, framework)) {
        if (framework.isTestClass(targetClass)) {
          return Collections.singletonList(framework);
        }
        if (framework.isPotentialTestClass(targetClass)) {
          result.add(framework);
        }
      }
    }
    return result;
  }

  private static boolean isAvailableFor(@NotNull Project project, @NotNull TestFramework framework) {
    if (framework instanceof JavaTestFramework) {
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      String markerClassFQName = ((JavaTestFramework)framework).getMarkerClassFQName();
      PsiClass c = JavaPsiFacade.getInstance(project).findClass(markerClassFQName, scope);
      return c != null;
    } else {
      return true;
    }
  }

  private TestIntegrationUtils() {
  }
}
