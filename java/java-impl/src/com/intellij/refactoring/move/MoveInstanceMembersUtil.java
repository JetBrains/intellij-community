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
package com.intellij.refactoring.move;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class MoveInstanceMembersUtil {
  private static final Logger LOG = Logger.getInstance("#" + MoveInstanceMembersUtil.class.getName());

  /**
   * @param member  nonstatic class member to search for class references in
   * @return Set<PsiMember> in result map may be null in case no member is needed, but class itself is.
   */
  public static Map<PsiClass, Set<PsiMember>> getThisClassesToMembers(final PsiMember member) {
    Map<PsiClass, Set<PsiMember>> map = new LinkedHashMap<>();
    getThisClassesToMembers (member, map, member);
    return map;
  }

  private static void getThisClassesToMembers(final PsiElement scope, final Map<PsiClass, Set<PsiMember>> map, final PsiMember refMember) {
    if (scope instanceof PsiExpression) {
      final PsiExpression expression = (PsiExpression)scope;
      if (!(scope instanceof PsiReferenceExpression) || !((PsiReferenceExpression)scope).isReferenceTo(refMember)) {
        final Pair<PsiMember, PsiClass> pair = getMemberAndClassReferencedByThis(expression);
        if (pair != null) {
          PsiClass refClass = pair.getSecond();
          PsiMember member = pair.getFirst();
          if (refClass != null) {
            boolean inherited = false;
            PsiClass parentClass = PsiTreeUtil.getParentOfType(scope, PsiClass.class, true);
            while (parentClass != null && PsiTreeUtil.isAncestor(refMember, parentClass, false)) {
              if (parentClass == refClass || parentClass.isInheritor(refClass, true)) {
                inherited = true;
                break;
              }
              parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
            }
            if (!inherited && !PsiTreeUtil.isAncestor(refMember, member, false)) {
              addReferencedMember(map, refClass, member);
            }
          }
        }

        if (expression instanceof PsiThisExpression) {
          final PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)expression).getQualifier();
          PsiClass thisClass = thisQualifier == null ? PsiTreeUtil.getParentOfType(expression, PsiClass.class, true) : ((PsiClass)thisQualifier.resolve());
          if (thisClass != null && !PsiTreeUtil.isAncestor( refMember,thisClass, false)) {
            addReferencedMember(map, thisClass, null);
          }
        }
      }
    }

    final PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      getThisClassesToMembers(child, map, refMember);
    }
  }

  private static void addReferencedMember(final Map<PsiClass, Set<PsiMember>> map, final PsiClass classReferenced, final PsiMember member) {
    Set<PsiMember> members = map.get(classReferenced);
    if (members == null) {
      members = new HashSet<>();
      map.put(classReferenced, members);
    }
    members.add(member);
  }

  @Nullable
  private static Pair<PsiMember, PsiClass> getMemberAndClassReferencedByThis(final PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
        if (resolved instanceof PsiMember && !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass referencedClass = getReferencedClass((PsiMember)resolved, qualifier, expression);
          return Pair.create((PsiMember)resolved, referencedClass);
        }
      }
    } else if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      final PsiExpression qualifier = newExpression.getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
        if (classReference != null) {
          final PsiClass resolved = (PsiClass)classReference.resolve();
          if (resolved != null && !resolved.hasModifierProperty(PsiModifier.STATIC)) {
            PsiClass referencedClass = getReferencedClass(resolved, qualifier, expression);
            return new Pair<>(resolved, referencedClass);
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiClass getReferencedClass(final PsiMember member, final PsiExpression exprQualifier, final PsiExpression expression) {
    if (exprQualifier != null) {
      final PsiType type = exprQualifier.getType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      return null;
    } else {
      PsiClass referencedClass = member.getContainingClass();
      if (referencedClass == null) return null;
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      assert parentClass != null;
      if (InheritanceUtil.isInheritorOrSelf(parentClass, referencedClass, false)) {
        referencedClass = parentClass;
      }
      return referencedClass;
    }
  }

  @Nullable
  public static PsiClass getClassReferencedByThis(final PsiExpression expression) {
    PsiClass enclosingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (enclosingClass == null) return null;
    final Pair<PsiMember, PsiClass> pair = getMemberAndClassReferencedByThis(expression);
    if (pair != null) return pair.getSecond();

    if (expression instanceof PsiThisExpression) {
      final PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)expression).getQualifier();
      if (thisQualifier == null) {
        return enclosingClass;
      }
      else {
        return (PsiClass)thisQualifier.resolve();
      }
    }
    return null;
  }

  public static void moveInitializerToConstructor(PsiElementFactory factory, PsiMethod constructor, PsiField field) {
    final PsiExpression initializer = field.getInitializer();
    PsiExpression initializerCopy = (PsiExpression)initializer.copy();
    final PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      try {
        String fieldName = field.getName();
        final PsiReferenceExpression refExpr = (PsiReferenceExpression)factory.createExpressionFromText(fieldName, body);
        if (refExpr.resolve() != null) fieldName = "this." + fieldName;
        PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(fieldName + "= y;", null);
        if (initializerCopy instanceof PsiArrayInitializerExpression) {
          PsiType type = initializer.getType();
          PsiNewExpression newExpression =
            (PsiNewExpression)factory.createExpressionFromText("new " + type.getCanonicalText() + "{}", body);
          newExpression.getArrayInitializer().replace(initializerCopy);
          initializerCopy = newExpression;
        }
        ((PsiAssignmentExpression)statement.getExpression()).getRExpression().replace(initializerCopy);
        statement = (PsiExpressionStatement)CodeStyleManager.getInstance(field.getManager().getProject()).reformat(statement);
        body.add(statement);
        initializer.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
