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

package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefJavaUtilImpl extends RefJavaUtil{

  @Override
  public void addReferences(@NotNull final PsiModifierListOwner psiFrom, @NotNull final RefJavaElement ref, @Nullable final PsiElement findIn) {
    final RefJavaElementImpl refFrom = (RefJavaElementImpl)ref;
    if (findIn == null) {
      return;
    }
    findIn.accept(
      new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          visitElement(reference);
          final PsiElement target = reference.resolve();

          if (target instanceof PsiClass) {
            final PsiClass aClass = (PsiClass)target;
            final RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(aClass);
            refFrom.addReference(refClass, aClass, psiFrom, false, true, null);
          }

          if (target instanceof PsiModifierListOwner && isDeprecated(target)) {
            refFrom.setUsesDeprecatedApi(true);
          }
        }

        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
          for (PsiReference reference : expression.getReferences()) {
            PsiElement resolve = reference.resolve();
            if (resolve instanceof PsiMember) {
              final RefElement refClass = refFrom.getRefManager().getReference(resolve);
              refFrom.addReference(refClass, resolve, psiFrom, false, true, null);
            }
          }
        }

        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);

          final JavaResolveResult result = expression.advancedResolve(false);
          final PsiElement psiResolved = result.getElement();

          if (psiResolved instanceof PsiModifierListOwner) {
            if (isDeprecated(psiResolved)) refFrom.setUsesDeprecatedApi(true);
          }

          RefElement refResolved = refFrom.getRefManager().getReference(psiResolved);
          refFrom.addReference(
            refResolved, psiResolved, psiFrom, PsiUtil.isAccessedForWriting(expression),
            PsiUtil.isAccessedForReading(expression), expression
          );

          if (refResolved instanceof RefMethod) {
            updateRefMethod(psiResolved, refResolved, expression, psiFrom, refFrom);
          }
          
          if (psiResolved instanceof PsiMember && result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
            final PsiClass containingClass = ((PsiMember)psiResolved).getContainingClass();
            if (containingClass != null) {
              RefElement refContainingClass = refFrom.getRefManager().getReference(containingClass);
              if (refContainingClass != null) {
                refFrom.addReference(refContainingClass, containingClass, psiFrom, false, true, expression);
              }
            }
          }
        }


        @Override public void visitEnumConstant(PsiEnumConstant enumConstant) {
          super.visitEnumConstant(enumConstant);
          processNewLikeConstruct(enumConstant.resolveConstructor(), enumConstant.getArgumentList());
        }

        @Override public void visitNewExpression(PsiNewExpression newExpr) {
          super.visitNewExpression(newExpr);
          PsiMethod psiConstructor = newExpr.resolveConstructor();
          final PsiExpressionList argumentList = newExpr.getArgumentList();

          RefMethod refConstructor = processNewLikeConstruct(psiConstructor, argumentList);

          if (refConstructor == null) {  // No explicit constructor referenced. Should use default one.
            PsiType newType = newExpr.getType();
            if (newType instanceof PsiClassType) {
              processClassReference(PsiUtil.resolveClassInType(newType), refFrom, psiFrom, true);
            }
          }
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
          super.visitLambdaExpression(expression);
          processFunctionalExpression(expression);
        }

        @Override
        public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
          super.visitMethodReferenceExpression(expression);
          processFunctionalExpression(expression);
        }

        private void processFunctionalExpression(PsiFunctionalExpression expression) {
          final PsiClass aClass = PsiUtil.resolveClassInType(expression.getFunctionalInterfaceType());
          if (aClass != null) {
            refFrom.addReference(refFrom.getRefManager().getReference(aClass), aClass, psiFrom, false, true, null);
            final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
            if (interfaceMethod != null) {
              refFrom.addReference(refFrom.getRefManager().getReference(interfaceMethod), interfaceMethod, psiFrom, false, true, null);
              refFrom.getRefManager().fireNodeMarkedReferenced(interfaceMethod, expression);
            }
          }
        }

        @Nullable
        private RefMethod processNewLikeConstruct(final PsiMethod psiConstructor, final PsiExpressionList argumentList) {
          if (psiConstructor != null) {
            if (isDeprecated(psiConstructor)) refFrom.setUsesDeprecatedApi(true);
          }

          RefMethodImpl refConstructor = (RefMethodImpl)refFrom.getRefManager().getReference(
            psiConstructor
          );
          refFrom.addReference(refConstructor, psiConstructor, psiFrom, false, true, null);

          if (argumentList != null) {
            PsiExpression[] psiParams = argumentList.getExpressions();
            for (PsiExpression param : psiParams) {
              param.accept(this);
            }

            if (refConstructor != null) {
              refConstructor.updateParameterValues(psiParams);
            }
          }
          return refConstructor;
        }

        @Override public void visitClass(PsiClass psiClass) {
          super.visitClass(psiClass);
          RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(psiClass);
          refFrom.addReference(refClass, psiClass, psiFrom, false, true, null);
        }

        @Override public void visitReturnStatement(PsiReturnStatement statement) {
          super.visitReturnStatement(statement);

          if (refFrom instanceof RefMethodImpl) {
            RefMethodImpl refMethod = (RefMethodImpl)refFrom;
            refMethod.updateReturnValueTemplate(statement.getReturnValue());
          }
        }

        @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
          super.visitClassObjectAccessExpression(expression);
          final PsiTypeElement operand = expression.getOperand();
          final PsiType type = operand.getType();
          if (type instanceof PsiClassType) {
            processClassReference(((PsiClassType)type).resolve(), refFrom, psiFrom, false);
          }
        }

        private void processClassReference(final PsiClass psiClass,
                                           final RefJavaElementImpl refFrom,
                                           final PsiModifierListOwner psiFrom,
                                           boolean defaultConstructorOnly) {
          if (psiClass != null) {
            RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(psiClass);

            if (refClass != null) {
              boolean hasConstructorsMarked = false;

              if (defaultConstructorOnly) {
                RefMethodImpl refDefaultConstructor = (RefMethodImpl)refClass.getDefaultConstructor();
                if (refDefaultConstructor != null) {
                  refDefaultConstructor.addInReference(refFrom);
                  refFrom.addOutReference(refDefaultConstructor);
                  hasConstructorsMarked = true;
                }
              }
              else {
                for (RefMethod cons : refClass.getConstructors()) {
                  if (cons instanceof RefImplicitConstructor) continue;
                  ((RefMethodImpl)cons).addInReference(refFrom);
                  refFrom.addOutReference(cons);
                  hasConstructorsMarked = true;
                }
              }

              if (!hasConstructorsMarked) {
                refFrom.addReference(refClass, psiClass, psiFrom, false, true, null);
              }
            }
          }
        }
      }
    );
  }

  private void updateRefMethod(PsiElement psiResolved,
                               RefElement refResolved,
                               PsiElement refExpression,
                               final PsiElement psiFrom,
                               final RefElement refFrom) {
    PsiMethod psiMethod = (PsiMethod)psiResolved;
    RefMethodImpl refMethod = (RefMethodImpl)refResolved;

    if (refExpression instanceof PsiMethodReferenceExpression) {
      PsiType returnType = psiMethod.getReturnType();
      if (!psiMethod.isConstructor() && !PsiType.VOID.equals(returnType)) {
        refMethod.setReturnValueUsed(true);
        addTypeReference(psiFrom, returnType, refFrom.getRefManager());
      }
      return;
    }
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(
      refExpression,
      PsiMethodCallExpression.class
    );
    if (call != null) {
      PsiType returnType = psiMethod.getReturnType();
      if (!psiMethod.isConstructor() && !PsiType.VOID.equals(returnType)) {
        if (!(call.getParent() instanceof PsiExpressionStatement)) {
          refMethod.setReturnValueUsed(true);
        }

        addTypeReference(psiFrom, returnType, refFrom.getRefManager());
      }

      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList.getExpressions().length > 0) {
        refMethod.updateParameterValues(argumentList.getExpressions());
      }

      final PsiExpression psiExpression = call.getMethodExpression().getQualifierExpression();
      if (psiExpression != null) {
        final PsiType usedType = psiExpression.getType();
        if (usedType != null) {
          final String fqName = psiMethod.getContainingClass().getQualifiedName();
          if (fqName != null) {
            final PsiClassType methodOwnerType = JavaPsiFacade.getInstance(call.getProject()).getElementFactory()
              .createTypeByFQClassName(fqName, GlobalSearchScope.allScope(psiMethod.getProject()));
            if (!usedType.equals(methodOwnerType)) {
              refMethod.setCalledOnSubClass(true);
            }
          }
        }
      }
    }
  }




  @Override
  public RefClass getTopLevelClass(@NotNull RefElement refElement) {
    RefEntity refParent = refElement.getOwner();

    while (refParent instanceof RefElement && !(refParent instanceof RefFile)) {
      refElement = (RefElementImpl)refParent;
      refParent = refParent.getOwner();
    }

    return refElement instanceof RefClass ? (RefClass)refElement : null;
  }

  @Override
  public boolean isInheritor(@NotNull RefClass subClass, RefClass superClass) {
    if (subClass == superClass) return true;

    for (RefClass baseClass : subClass.getBaseClasses()) {
      if (isInheritor(baseClass, superClass)) return true;
    }

    return false;
  }

  @Override
  @Nullable
  public String getPackageName(RefEntity refEntity) {
    if (refEntity instanceof RefProject) {
      return null;
    }
    RefPackage refPackage = getPackage(refEntity);

    return refPackage == null ? InspectionsBundle.message("inspection.reference.default.package") : refPackage.getQualifiedName();
  }

  @NotNull
  @Override
  public String getAccessModifier(@NotNull PsiModifierListOwner psiElement) {
     if (psiElement instanceof PsiParameter) return PsiModifier.PACKAGE_LOCAL;

     PsiModifierList list = psiElement.getModifierList();
     String result = PsiModifier.PACKAGE_LOCAL;

     if (list != null) {
       if (list.hasModifierProperty(PsiModifier.PRIVATE)) {
         result = PsiModifier.PRIVATE;
       }
       else if (list.hasModifierProperty(PsiModifier.PROTECTED)) {
         result = PsiModifier.PROTECTED;
       }
       else if (list.hasModifierProperty(PsiModifier.PUBLIC)) {
         result = PsiModifier.PUBLIC;
       }
       else if (psiElement.getParent() instanceof PsiClass) {
         PsiClass ownerClass = (PsiClass)psiElement.getParent();
         if (ownerClass.isInterface()) {
           result = PsiModifier.PUBLIC;
         }
         if (ownerClass.isEnum() && result.equals(PsiModifier.PACKAGE_LOCAL)) {
           result = PsiModifier.PRIVATE;
         }
       }
     }

     return result;
   }

   @Override
   @Nullable public RefClass getOwnerClass(RefManager refManager, PsiElement psiElement) {
     while (psiElement != null && !(psiElement instanceof PsiClass)) {
       psiElement = psiElement.getParent();
     }

     return psiElement != null ? (RefClass)refManager.getReference(psiElement) : null;
   }

   @Override
   @Nullable public RefClass getOwnerClass(RefElement refElement) {
     RefEntity parent = refElement.getOwner();

     while (!(parent instanceof RefClass) && parent instanceof RefElement) {
       parent = parent.getOwner();
     }

     if (parent instanceof RefClass) return (RefClass)parent;

     return null;
   }



   @Override
   public boolean isMethodOnlyCallsSuper(PsiMethod method) {
     boolean hasStatements = false;
     PsiCodeBlock body = method.getBody();
     if (body != null) {
       PsiStatement[] statements = body.getStatements();
       for (PsiStatement statement : statements) {
         boolean isCallToSameSuper = false;
         if (statement instanceof PsiExpressionStatement) {
           isCallToSameSuper = isCallToSuperMethod(((PsiExpressionStatement)statement).getExpression(), method);
         }
         else if (statement instanceof PsiReturnStatement) {
           PsiExpression expression = ((PsiReturnStatement)statement).getReturnValue();
           isCallToSameSuper = expression == null || isCallToSuperMethod(expression, method);
         }

         hasStatements = true;
         if (isCallToSameSuper) continue;

         return false;
       }
     }

     if (hasStatements) {
       final PsiMethod[] superMethods = method.findSuperMethods();
       for (PsiMethod superMethod : superMethods) {
         if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(superMethod.getModifierList()),
                                    VisibilityUtil.getVisibilityModifier(method.getModifierList())) > 0) return false;
       }
     }
     return hasStatements;
   }

   @Override
   public boolean isCallToSuperMethod(PsiExpression expression, PsiMethod method) {
     if (expression instanceof PsiMethodCallExpression) {
       PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
       if (methodCall.getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
         PsiMethod superMethod = (PsiMethod)methodCall.getMethodExpression().resolve();
         if (superMethod == null || !MethodSignatureUtil.areSignaturesEqual(method, superMethod)) return false;
         PsiExpression[] args = methodCall.getArgumentList().getExpressions();
         PsiParameter[] parms = method.getParameterList().getParameters();

         for (int i = 0; i < args.length; i++) {
           PsiExpression arg = args[i];
           if (!(arg instanceof PsiReferenceExpression)) return false;
           if (!parms[i].equals(((PsiReferenceExpression)arg).resolve())) return false;
         }

         return true;
       }
     }

     return false;
   }

   @Override
   public int compareAccess(String a1, String a2) {
     int i1 = getAccessNumber(a1);
     int i2 = getAccessNumber(a2);

     if (i1 == i2) return 0;
     if (i1 < i2) return -1;
     return 1;
   }

   @SuppressWarnings("StringEquality")
     private static int getAccessNumber(String a) {
     if (a == PsiModifier.PRIVATE) {
       return 0;
     }
     if (a == PsiModifier.PACKAGE_LOCAL) {
       return 1;
     }
     if (a == PsiModifier.PROTECTED) {
       return 2;
     }
     if (a == PsiModifier.PUBLIC) return 3;

     return -1;
   }

  @Override
  public void setAccessModifier(@NotNull RefJavaElement refElement, @NotNull String newAccess) {
    ((RefJavaElementImpl)refElement).setAccessModifier(newAccess);
  }

  @Override
  public void setIsStatic(RefJavaElement refElement, boolean isStatic) {
    ((RefJavaElementImpl)refElement).setIsStatic(isStatic);
  }

  @Override
  public void setIsFinal(RefJavaElement refElement, boolean isFinal) {
    ((RefJavaElementImpl)refElement).setIsFinal(isFinal);
  }

  @Override
  public void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager) {
    addTypeReference(psiElement, psiType, refManager, null);
  }

  @Override
  public void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager, @Nullable RefJavaElement refMethod) {
    if (psiType != null) {
      final RefClass ownerClass = getOwnerClass(refManager, psiElement);
      if (ownerClass != null) {
        psiType = psiType.getDeepComponentType();
        if (psiType instanceof PsiClassType) {
          PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
          if (psiClass != null && refManager.belongsToScope(psiClass)) {
            RefClassImpl refClass = (RefClassImpl)refManager.getReference(psiClass);
            if (refClass != null) {
              refClass.addTypeReference(ownerClass);
              if (refMethod != null) {
                refClass.addClassExporter(refMethod);
              }
            }
          }
          else {
            ((RefManagerImpl)refManager).fireNodeMarkedReferenced(psiClass, psiElement);
          }
        }
      }
    }
  }
}
