/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static com.intellij.codeInsight.completion.BasicExpressionCompletionContributor.createKeywordLookupItem;

class JavaModuleCompletionContributor {
  static boolean isModuleFile(@NotNull PsiFile file) {
    return PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) && PsiUtil.isLanguageLevel9OrHigher(file);
  }

  static void addVariants(@NotNull PsiElement position, @NotNull CompletionResultSet resultSet) {
    Consumer<LookupElement> result = element -> {
      if (element.getLookupString().startsWith(resultSet.getPrefixMatcher().getPrefix())) {
        resultSet.addElement(element);
      }
    };

    if (position instanceof PsiIdentifier) {
      PsiElement context = position.getParent();
      if (context instanceof PsiErrorElement) context = context.getParent();

      if (context instanceof PsiJavaFile) {
        addFileHeaderKeywords(position, result);
      }
      else if (context instanceof PsiJavaModule) {
        addModuleStatementKeywords(position, result);
      }
      else if (context instanceof PsiProvidesStatement) {
        addProvidesStatementKeywords(position, result);
      }
      else if (context instanceof PsiJavaModuleReferenceElement) {
        addRequiresStatementKeywords(context, position, result);
        addModuleReferences(context, result);
      }
      else if (context instanceof PsiJavaCodeReferenceElement) {
        addClassOrPackageReferences(context, result, resultSet);
      }
    }
  }

  private static void addFileHeaderKeywords(PsiElement position, Consumer<LookupElement> result) {
    PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
    if (prev == null) {
      result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.OPEN), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
    else if (PsiUtil.isJavaToken(prev, JavaTokenType.OPEN_KEYWORD)) {
      result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addModuleStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
    result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.REQUIRES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.EXPORTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.OPENS), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.USES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.PROVIDES), TailType.HUMBLE_SPACE_BEFORE_WORD));
  }

  private static void addProvidesStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
    result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.WITH), TailType.HUMBLE_SPACE_BEFORE_WORD));
  }

  private static void addRequiresStatementKeywords(PsiElement context, PsiElement position, Consumer<LookupElement> result) {
    if (context.getParent() instanceof PsiRequiresStatement) {
      result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.TRANSITIVE), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addModuleReferences(PsiElement context, Consumer<LookupElement> result) {
    PsiElement statement = context.getParent();
    if (!(statement instanceof PsiJavaModule)) {
      PsiElement host = statement.getParent();
      if (host instanceof PsiJavaModule) {
        String hostName = ((PsiJavaModule)host).getName();
        Project project = context.getProject();
        JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
        GlobalSearchScope scope = ProjectScope.getAllScope(project);
        for (String name : index.getAllKeys(project)) {
          if (!name.equals(hostName) && index.get(name, project, scope).size() == 1) {
            result.consume(new OverrideableSpace(LookupElementBuilder.create(name), TailType.SEMICOLON));
          }
        }
      }
    }
  }

  private static void addClassOrPackageReferences(PsiElement context, Consumer<LookupElement> result, CompletionResultSet resultSet) {
    PsiElement refOwner = context.getParent();
    if (refOwner instanceof PsiPackageAccessibilityStatement) {
      Module module = ModuleUtilCore.findModuleForPsiElement(context);
      PsiPackage topPackage = JavaPsiFacade.getInstance(context.getProject()).findPackage("");
      if (module != null && topPackage != null) {
        processPackage(topPackage, module.getModuleScope(false), result);
      }
    }
    else if (refOwner instanceof PsiUsesStatement) {
      processClasses(context.getProject(), null, resultSet, SERVICE_FILTER, TailType.SEMICOLON);
    }
    else if (refOwner instanceof PsiProvidesStatement) {
      processClasses(context.getProject(), null, resultSet, SERVICE_FILTER, TailType.HUMBLE_SPACE_BEFORE_WORD);
    }
    else if (refOwner instanceof PsiReferenceList) {
      PsiElement statement = refOwner.getParent();
      if (statement instanceof PsiProvidesStatement) {
        PsiJavaCodeReferenceElement intRef = ((PsiProvidesStatement)statement).getInterfaceReference();
        if (intRef != null) {
          PsiElement service = intRef.resolve();
          Module module = ModuleUtilCore.findModuleForPsiElement(context);
          if (service instanceof PsiClass && module != null) {
            Predicate<PsiClass> filter = psiClass -> !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
                                                     InheritanceUtil.isInheritorOrSelf(psiClass, (PsiClass)service, true);
            processClasses(context.getProject(), module.getModuleScope(false), resultSet, filter, TailType.SEMICOLON);
          }
        }
      }
    }
    else if (refOwner instanceof PsiAnnotation) {
      processClasses(context.getProject(), null, resultSet, ANNOTATION_FILTER, TailType.NONE);
    }
  }

  private static void processPackage(PsiPackage pkg, GlobalSearchScope scope, Consumer<LookupElement> result) {
    String packageName = pkg.getQualifiedName();
    if (isQualified(packageName) && !PsiUtil.isPackageEmpty(pkg.getDirectories(scope), packageName)) {
      result.consume(new OverrideableSpace(lookupElement(pkg), TailType.NONE));
    }
    for (PsiPackage subPackage : pkg.getSubPackages(scope)) {
      processPackage(subPackage, scope, result);
    }
  }

  private static final Predicate<PsiClass> SERVICE_FILTER =
    cl -> !cl.isEnum() && !cl.isAnnotationType() && cl.hasModifierProperty(PsiModifier.PUBLIC);

  private static final Predicate<PsiClass> ANNOTATION_FILTER = cl -> cl.isAnnotationType();

  private static void processClasses(Project project,
                                     GlobalSearchScope scope,
                                     CompletionResultSet resultSet,
                                     Predicate<PsiClass> filter,
                                     TailType tail) {
    GlobalSearchScope _scope = scope != null ? scope : ProjectScope.getAllScope(project);
    AllClassesGetter.processJavaClasses(resultSet.getPrefixMatcher(), project, _scope, psiClass -> {
      if (isQualified(psiClass.getQualifiedName()) && filter.test(psiClass)) {
        resultSet.addElement(new OverrideableSpace(lookupElement(psiClass), tail));
      }
      return true;
    });
  }

  private static LookupElementBuilder lookupElement(PsiNamedElement e) {
    LookupElementBuilder lookup = LookupElementBuilder.create(e).withInsertHandler(FQN_INSERT_HANDLER);
    String fqn = e instanceof PsiClass ? ((PsiClass)e).getQualifiedName() : ((PsiQualifiedNamedElement)e).getQualifiedName();
    return fqn != null ? lookup.withPresentableText(fqn) : lookup;
  }

  private static boolean isQualified(String name) {
    return name != null && name.indexOf('.') > 0;
  }

  private static final InsertHandler<LookupElement> FQN_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      Object e = item.getObject();
      String fqn = e instanceof PsiClass ? ((PsiClass)e).getQualifiedName() : ((PsiQualifiedNamedElement)e).getQualifiedName();
      if (fqn != null && !fqn.equals("java.lang." + item.getLookupString())) {
        int start = JavaCompletionUtil.findQualifiedNameStart(context);
        context.getDocument().replaceString(start, context.getTailOffset(), fqn);
      }
    }
  };
}