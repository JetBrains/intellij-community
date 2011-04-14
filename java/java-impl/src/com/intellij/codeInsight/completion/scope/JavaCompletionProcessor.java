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
package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:13:27
 * To change this template use Options | File Templates.
 */
public class JavaCompletionProcessor extends BaseScopeProcessor implements ElementClassHint {
  public static final Key<Condition<String>> NAME_FILTER = Key.create("NAME_FILTER");
  public static final Key<Boolean> JAVA_COMPLETION = Key.create("JAVA_COMPLETION");

  private boolean myStatic = false;
  private PsiElement myDeclarationHolder = null;
  private final Set<Object> myResultNames = new THashSet<Object>();
  private final List<CompletionElement> myResults;
  private final PsiElement myElement;
  private final PsiElement myScope;
  private CodeInsightSettings mySettings = null;
  private final ElementFilter myFilter;
  private boolean myMembersFlag = false;
  private PsiType myQualifierType = null;
  private PsiClass myQualifierClass = null;
  private final Condition<String> myMatcher;
  private final boolean myCheckAccess;
  private final Set<PsiField> myNonInitializedFields = new HashSet<PsiField>();

  public JavaCompletionProcessor(PsiElement element, ElementFilter filter, final boolean checkAccess, boolean checkInitialized, @Nullable Condition<String> nameCondition) {
    myCheckAccess = checkAccess;
    mySettings = CodeInsightSettings.getInstance();
    myResults = new ArrayList<CompletionElement>();
    myElement = element;
    myMatcher = nameCondition;
    myFilter = filter;
    PsiElement scope = element;
    if (JavaResolveUtil.isInJavaDoc(myElement)) myMembersFlag = true;
    while(scope != null && !(scope instanceof PsiFile) && !(scope instanceof PsiClass)){
      scope = scope.getContext();
    }
    myScope = scope;
    if (!(element.getContainingFile() instanceof PsiJavaFile)) {
      myMembersFlag = true;
    }

    PsiElement elementParent = element.getContext();
    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        final PsiJavaCodeReferenceElement qSuper = ((PsiSuperExpression)qualifier).getQualifier();
        if (qSuper == null) {
          myQualifierClass = JavaResolveUtil.getContextClass(myElement);
        } else {
          final PsiElement target = qSuper.resolve();
          myQualifierClass = target instanceof PsiClass ? (PsiClass)target : null;
        }
        if (myQualifierClass != null) {
          myQualifierType = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createType(myQualifierClass);
        }
      }
      else if (qualifier != null) {
        myQualifierType = qualifier.getType();
        myQualifierClass = PsiUtil.resolveClassInType(myQualifierType);
        if (myQualifierType == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
          if (target instanceof PsiClass) {
            myQualifierClass = (PsiClass)target;
          }
        }
      }
    }

    if (checkInitialized) {
      myNonInitializedFields.addAll(getNonInitializedFields(element));
    }
  }

  private static boolean isInitializedImplicitly(PsiField field) {
    field = JavaCompletionUtil.getOriginalElement(field);
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensions()) {
      if (provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  private static Set<PsiField> getNonInitializedFields(PsiElement element) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiClass.class);
    if (statement == null || method == null || !method.isConstructor()) {
      return Collections.emptySet();
    }

    PsiElement parent = element.getParent();
    while (parent != statement) {
      PsiElement next = parent.getParent();
      if (next instanceof PsiAssignmentExpression && parent == ((PsiAssignmentExpression)next).getLExpression()) {
        return Collections.emptySet();
      }
      if (parent instanceof PsiReferenceExpression && next instanceof PsiExpressionStatement) {
        return Collections.emptySet();
      }
      parent = next;
    }

    final Set<PsiField> fields = new HashSet<PsiField>();
    final PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;
    for (PsiField field : containingClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() == null && !isInitializedImplicitly(field)) {
        fields.add(field);
      }
    }

    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiExpression lExpression = expression.getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            //noinspection SuspiciousMethodCalls
            fields.remove(((PsiReferenceExpression)lExpression).resolve());
          }
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiReferenceExpression methodExpression = expression.getMethodExpression();
          if (methodExpression.textMatches("this")) {
            fields.clear();
          }
        }
        super.visitMethodCallExpression(expression);
      }
    });
    return fields;
  }

  public void handleEvent(Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC){
      myStatic = true;
    }
    if(event == JavaScopeProcessorEvent.CHANGE_LEVEL){
      myMembersFlag = true;
    }
    if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
      myDeclarationHolder = (PsiElement)associated;
    }
  }

  public boolean execute(PsiElement element, ResolveState state) {
    //noinspection SuspiciousMethodCalls
    if (myNonInitializedFields.contains(element)) {
      return true;
    }

    if (!(element instanceof PsiClass) && element instanceof PsiModifierListOwner) {
      PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
      if (myStatic) {
        if (!modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
          // we don't need non static method in static context.
          return true;
        }
      }
      else {
        if (!mySettings.SHOW_STATIC_AFTER_INSTANCE
            && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)
            && !myMembersFlag) {
          // according settings we don't need to process such fields/methods
          return true;
        }
      }
    }
    final PsiElement elementParent = myElement.getParent();
    if (element instanceof PsiPackage && myScope instanceof PsiClass) {
      if (!(elementParent instanceof PsiQualifiedReference && ((PsiQualifiedReference)elementParent).getQualifier() != null)) {
        return true;
      }
    }

    if (satisfies(element, state) && isAccessible(element)) {
      CompletionElement element1 = new CompletionElement(myQualifierType, element, state.get(PsiSubstitutor.KEY), myQualifierClass);
      if (myResultNames.add(element1.getUniqueId())) {
        myResults.add(element1);
      }
    }
    return true;
  }

  public boolean satisfies(@NotNull PsiElement element, @NotNull ResolveState state) {
    final String name = PsiUtil.getName(element);
    if (StringUtil.isNotEmpty(name) && (myMatcher == null || myMatcher.value(name))) {
      if (myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(new CandidateInfo(element, state.get(PsiSubstitutor.KEY)), myElement)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public PsiType getQualifierType() {
    return myQualifierType;
  }

  private boolean isAccessible(final PsiElement element) {
    if (!myCheckAccess) return true;
    if (!(element instanceof PsiMember)) return true;

    PsiMember member = (PsiMember)element;
    return JavaPsiFacade.getInstance(element.getProject()).getResolveHelper().isAccessible(member, member.getModifierList(), myElement, myQualifierClass, myDeclarationHolder);
  }

  public void setCompletionElements(@NotNull Object[] elements) {
    for (Object element: elements) {
      myResults.add(new CompletionElement(null, element, PsiSubstitutor.EMPTY, myQualifierClass));
    }
  }

  public Set<CompletionElement> getResults(){
    return new THashSet<CompletionElement>(myResults);
  }

  public void clear() {
    myResults.clear();
  }

  public boolean shouldProcess(DeclarationKind kind) {
    switch (kind) {
      case CLASS:
        return myFilter.isClassAcceptable(PsiClass.class);

      case FIELD:
        return myFilter.isClassAcceptable(PsiField.class);

      case METHOD:
        return myFilter.isClassAcceptable(PsiMethod.class);

      case PACKAGE:
        return myFilter.isClassAcceptable(PsiPackage.class);

      case VARIABLE:
        return myFilter.isClassAcceptable(PsiVariable.class);

      case ENUM_CONST:
        return myFilter.isClassAcceptable(PsiEnumConstant.class);
    }

    return false;
  }

  @Override
  public <T> T getHint(Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }
    if (hintKey == NAME_FILTER) {
      return (T)myMatcher;
    }
    if (hintKey == JAVA_COMPLETION) {
      return (T)Boolean.TRUE;
    }

    return super.getHint(hintKey);
  }
}
