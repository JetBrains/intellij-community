// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

public class RefJavaUtilImpl extends RefJavaUtil {
  private static final Logger LOG = Logger.getInstance(RefJavaUtilImpl.class);

  @Override
  public void addReferences(@NotNull PsiModifierListOwner psiFrom, @NotNull RefJavaElement ref, @Nullable PsiElement findIn) {
    UDeclaration decl = UastContextKt.toUElement(psiFrom, UDeclaration.class);
    UElement uFindIn = UastContextKt.toUElement(findIn);
    if (decl != null && findIn != null) {
      addReferencesTo(decl, ref, new UElement[]{uFindIn});
    }
  }

  @Override
  public void addReferencesTo(@NotNull final UDeclaration decl, @NotNull final RefJavaElement ref, @Nullable final UElement[] findIn) {
    final RefJavaElementImpl refFrom = (RefJavaElementImpl)ref;
    if (findIn == null) {
      return;
    }
    for (UElement element : findIn) {
      if (element == null) continue;
      element.accept(new AbstractUastVisitor() {
                       @Override
                       public boolean visitEnumConstant(@NotNull UEnumConstant node) {
                         processNewLikeConstruct(node.resolve(), node.getValueArguments());
                         return false;
                       }

                       @Override
                       public boolean visitAnnotation(@NotNull UAnnotation node) {
                         PsiClass javaClass = node.resolve();
                         if (javaClass != null) {
                           final RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(javaClass.getOriginalElement());
                           refFrom.addReference(refClass, javaClass.getOriginalElement(), decl, false, true, null);
                         }
                         return false;
                       }

                       @Override
                       public boolean visitTypeReferenceExpression(@NotNull UTypeReferenceExpression node) {
                         PsiType type = node.getType();
                         visitTypeRefs(type);
                         return false;
                       }

                       private void visitTypeRefs(PsiType type) {
                         type = type.getDeepComponentType();
                         if (type instanceof PsiClassType) {
                           type.accept(new PsiTypeVisitor<Void>() {
                             @Nullable
                             @Override
                             public Void visitClassType(PsiClassType classType) {
                               for (PsiType parameter : classType.getParameters()) {
                                 parameter.accept(this);
                               }
                               UClass target = UastContextKt.toUElement(classType.resolve(), UClass.class);
                               if (target != null) {
                                 final RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(target.getSourcePsi());
                                 refFrom.addReference(refClass, target.getSourcePsi(), decl, false, true, null);
                               }
                               return null;
                             }
                           });
                         }
                       }

                       @Override
                       public boolean visitVariable(@NotNull UVariable node) {
                         visitTypeRefs(node.getType());
                         return false;
                       }

                       @Override
                       public boolean visitSimpleNameReferenceExpression(@NotNull USimpleNameReferenceExpression node) {
                         final PsiElement target = node.resolve();

                         visitReferenceExpression(node);
                         if (target instanceof PsiClass) {
                           final PsiClass aClass = (PsiClass)target;
                           final RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(aClass);
                           refFrom.addReference(refClass, aClass, decl, false, true, null);
                         }
                         return false;
                       }

                       @Override
                       public boolean visitLiteralExpression(@NotNull ULiteralExpression node) {
                         PsiElement sourcePsi = node.getSourcePsi();
                         if (sourcePsi != null) {
                           for (PsiReference reference : sourcePsi.getReferences()) {
                             PsiElement resolve = reference.resolve();
                             if (resolve instanceof PsiMember) {
                               final RefElement refResolved = refFrom.getRefManager().getReference(resolve);
                               refFrom.addReference(refResolved, resolve, decl, false, true, null);
                               if (refResolved instanceof RefMethod) {
                                 updateRefMethod(resolve, refResolved, node, decl, refFrom);
                               }
                             }
                           }
                         }
                         return false;
                       }

                       @Override
                       public boolean visitPrefixExpression(@NotNull UPrefixExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitPostfixExpression(@NotNull UPostfixExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitUnaryExpression(@NotNull UUnaryExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitBinaryExpression(@NotNull UBinaryExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
                         visitReferenceExpression(node);
                         return false;
                       }

                       @Override
                       public boolean visitCallableReferenceExpression(@NotNull UCallableReferenceExpression node) {
                         visitReferenceExpression(node);
                         processFunctionalExpression(node, getFunctionalInterfaceType(node));
                         return false;
                       }

                       @Override
                       public boolean visitObjectLiteralExpression(@NotNull UObjectLiteralExpression node) {
                         visitReferenceExpression(node);
                         visitClass(node.getDeclaration());
                         return false;
                       }

                       @Override
                       public boolean visitCallExpression(@NotNull UCallExpression node) {
                         visitReferenceExpression(node);
                         if (node instanceof UObjectLiteralExpression) {
                           visitClass(((UObjectLiteralExpression)node).getDeclaration());
                         }
                         if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                           PsiMethod resolvedMethod = node.resolve();
                           final List<UExpression> argumentList = node.getValueArguments();
                           RefMethod refConstructor = processNewLikeConstruct(resolvedMethod, argumentList);

                           if (refConstructor == null) {  // No explicit constructor referenced. Should use default one.
                             UReferenceExpression reference = node.getClassReference();
                             if (reference != null) {
                               PsiElement constructorClass = reference.resolve();
                               if (constructorClass instanceof PsiClass) {
                                 processClassReference((PsiClass)constructorClass, refFrom, decl, true);
                               }
                             }
                           }
                         }
                         try {
                           node.getTypeArguments().forEach(this::visitTypeRefs);
                         }
                         catch (UnsupportedOperationException e) {
                           //TODO happens somewhere in kotlin plugin. Please assign those exception for Dmitry Batkovich
                           LOG.error(e);
                         }
                         return false;
                       }

                       private void visitReferenceExpression(UExpression node) {
                         UElement uastParent = node.getUastParent();
                         if (uastParent instanceof UQualifiedReferenceExpression && ((UQualifiedReferenceExpression)uastParent).getSelector() == node) {
                           return;
                         }
                         PsiElement psiResolved = null;
                         if (node instanceof UResolvable) {
                           psiResolved = ((UResolvable)node).resolve();
                         }
                         else if (node instanceof UBinaryExpression) {
                           psiResolved = ((UBinaryExpression)node).resolveOperator();
                         }
                         else if (node instanceof UUnaryExpression) {
                           psiResolved = ((UUnaryExpression)node).resolveOperator();
                         }
                         if (psiResolved == null) {
                           psiResolved = tryFindKotlinParameter(node, decl);
                         }
                         if (psiResolved instanceof LightElement) {
                           psiResolved = psiResolved.getNavigationElement();
                         }
                         RefElement refResolved = refFrom.getRefManager().getReference(psiResolved);
                         boolean writing = isAccessedForWriting(node);
                         boolean reading = isAccessedForReading(node);
                         refFrom.addReference(refResolved, psiResolved, decl, writing, reading, node);

                         if (refResolved instanceof RefMethod) {
                           updateRefMethod(psiResolved, refResolved, node, decl, refFrom);
                         }

                         if (psiResolved instanceof PsiMember) {
                           //TODO support kotlin
                           addClassReferenceForStaticImport(node, (PsiMember)psiResolved, refFrom, decl);
                         }
                       }

                       @Override
                       public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
                         processFunctionalExpression(node, node.getFunctionalInterfaceType());
                         return false;
                       }


                       private void processFunctionalExpression(UExpression expression, PsiType type) {
                         PsiElement aClass = PsiUtil.resolveClassInType(type);
                         if (aClass != null) {
                           aClass = ((PsiClass)aClass).getSourceElement();
                         }
                         if (aClass != null) {
                           refFrom.addReference(refFrom.getRefManager().getReference(aClass), aClass, decl, false, true, null);
                           final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(type);
                           if (interfaceMethod != null) {
                             refFrom.addReference(refFrom.getRefManager().getReference(interfaceMethod), interfaceMethod, decl, false, true, null);
                             refFrom.getRefManager().fireNodeMarkedReferenced(interfaceMethod, expression.getSourcePsi());
                           }
                         }
                       }

                       @Nullable
                       private RefMethod processNewLikeConstruct(final PsiMethod javaConstructor, final List<UExpression> argumentList) {
                         if (javaConstructor == null) return null;
                         RefMethodImpl refConstructor = (RefMethodImpl)refFrom.getRefManager().getReference(javaConstructor.getOriginalElement());
                         refFrom.addReference(refConstructor, javaConstructor, decl, false, true, null);

                         for (UExpression arg : argumentList) {
                           arg.accept(this);
                         }

                         if (refConstructor != null) {
                           refConstructor.updateParameterValues(argumentList, javaConstructor);
                         }
                         return refConstructor;
                       }

                       @Override
                       public boolean visitClass(@NotNull UClass uClass) {
                         for (UTypeReferenceExpression type : uClass.getUastSuperTypes()) {
                           type.accept(this);
                         }
                         RefClassImpl refClass = (RefClassImpl)refFrom.getRefManager().getReference(uClass.getSourcePsi());
                         refFrom.addReference(refClass, uClass.getSourcePsi(), decl, false, true, null);
                         return true;
                       }

                       @Override
                       public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                         if (refFrom instanceof RefMethodImpl) {
                           RefMethodImpl refMethod = (RefMethodImpl)refFrom;
                           refMethod.updateReturnValueTemplate(node.getReturnExpression());
                         }
                         return false;
                       }

                       @Override
                       public boolean visitClassLiteralExpression(@NotNull UClassLiteralExpression node) {
                         final PsiType type = node.getType();
                         if (type instanceof PsiClassType) {
                           processClassReference(((PsiClassType)type).resolve(), refFrom, decl, false);
                         }
                         return false;
                       }

                       private void processClassReference(final PsiClass psiClass,
                                                          final RefJavaElementImpl refFrom,
                                                          final UDeclaration from,
                                                          boolean defaultConstructorOnly) {
                         if (psiClass != null) {
                           RefClassImpl refClass = ObjectUtils.tryCast(refFrom.getRefManager().getReference(psiClass.getNavigationElement()), RefClassImpl.class);

                           if (refClass != null) {
                             boolean hasConstructorsMarked = false;

                             if (defaultConstructorOnly) {
                               WritableRefElement refDefaultConstructor = (WritableRefElement)refClass.getDefaultConstructor();
                               if (refDefaultConstructor != null) {
                                 refDefaultConstructor.addInReference(refFrom);
                                 refFrom.addOutReference(refDefaultConstructor);
                                 hasConstructorsMarked = true;
                               }
                             }
                             else {
                               for (RefMethod cons : refClass.getConstructors()) {
                                 if (cons instanceof RefImplicitConstructor) continue;
                                 ((WritableRefElement)cons).addInReference(refFrom);
                                 refFrom.addOutReference(cons);
                                 hasConstructorsMarked = true;
                               }
                             }

                             if (!hasConstructorsMarked) {
                               refFrom.addReference(refClass, psiClass, from, false, true, null);
                             }
                           }
                         }
                       }
                     }
      );
    }
  }

  private static void addClassReferenceForStaticImport(UExpression node,
                                                       PsiMember psiResolved,
                                                       RefJavaElementImpl refFrom, UDeclaration decl) {
    PsiElement sourcePsi = node.getSourcePsi();
    if (sourcePsi instanceof PsiReferenceExpression) {
      JavaResolveResult result = ((PsiReferenceExpression)sourcePsi).advancedResolve(false);
      if (result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        final PsiClass containingClass = psiResolved.getContainingClass();
        if (containingClass != null) {
          RefElement refContainingClass = refFrom.getRefManager().getReference(containingClass);
          if (refContainingClass != null) {
            refFrom.addReference(refContainingClass, containingClass, decl, false, true, node);
          }
        }
      }
    }
  }

  private static PsiElement tryFindKotlinParameter(@NotNull UExpression node,
                                                   @NotNull UDeclaration decl) {
    //TODO see KT-25524
    if (node instanceof UCallExpression && "invoke".equals(((UCallExpression)node).getMethodName())) {
      UIdentifier identifier = ((UCallExpression)node).getMethodIdentifier();
      if (identifier != null) {
        String name = identifier.getName();
        if (decl instanceof UMethod) {
          UParameter parameter = ((UMethod)decl).getUastParameters().stream().filter(p -> name.equals(p.getName())).findAny().orElse(null);
          if (parameter != null) {
            return parameter.getSourcePsi();
          }
        }
      }
    }
    return null;
  }

  private void updateRefMethod(PsiElement psiResolved,
                               RefElement refResolved,
                               UExpression refExpression,
                               final UElement uFrom,
                               final RefElement refFrom) {
    UMethod uMethod = ObjectUtils.notNull(UastContextKt.toUElement(psiResolved, UMethod.class));
    RefMethodImpl refMethod = (RefMethodImpl)refResolved;

    if (refExpression instanceof UCallableReferenceExpression) {
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor() &&
          !PsiType.VOID
            .equals(LambdaUtil.getFunctionalInterfaceReturnType(getFunctionalInterfaceType((UCallableReferenceExpression)refExpression)))) {
        refMethod.setReturnValueUsed(true);
        addTypeReference(uFrom, returnType, refFrom.getRefManager());
      }
      return;
    }
    if (refExpression instanceof ULiteralExpression) { //references in literal expressions
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor() && !PsiType.VOID.equals(returnType)) {
        refMethod.setReturnValueUsed(true);
        addTypeReference(uFrom, returnType, refFrom.getRefManager());
      }
      return;
    }

    UCallExpression call = null;
    if (refExpression instanceof UCallExpression) {
      call = (UCallExpression)refExpression;
    }
    else if (refExpression instanceof UQualifiedReferenceExpression) {
      call = ObjectUtils.tryCast(((UQualifiedReferenceExpression)refExpression).getSelector(), UCallExpression.class);
    }
    if (call != null) {
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor() && !PsiType.VOID.equals(returnType)) {
        PsiExpression expression = ObjectUtils.tryCast(call.getJavaPsi(), PsiExpression.class);
        if (expression == null || !ExpressionUtils.isVoidContext(expression)) {
          refMethod.setReturnValueUsed(true);
        }

        addTypeReference(uFrom, returnType, refFrom.getRefManager());
      }

      List<UExpression> argumentList = call.getValueArguments();
      if (!argumentList.isEmpty()) {
        refMethod.updateParameterValues(argumentList, psiResolved);
      }

      final UExpression uExpression = call.getReceiver();
      if (uExpression != null) {
        final PsiType usedType = uExpression.getExpressionType();
        if (usedType != null) {
          UClass containingClass = UDeclarationKt.getContainingDeclaration(uMethod, UClass.class);
          final String fqName;
          if (containingClass != null) {
            fqName = containingClass.getQualifiedName();
            if (fqName != null) {
              final PsiClassType methodOwnerType = JavaPsiFacade.getElementFactory(psiResolved.getProject())
                .createTypeByFQClassName(fqName, GlobalSearchScope.allScope(psiResolved.getProject()));
              if (!usedType.equals(methodOwnerType)) {
                refMethod.setCalledOnSubClass(true);
              }
            }
          }
        }
      }
    }
  }

  private static PsiType getFunctionalInterfaceType(UCallableReferenceExpression expression) {
    PsiElement psi = expression.getSourcePsi();
    if (psi instanceof PsiFunctionalExpression) {
      return ((PsiFunctionalExpression)psi).getFunctionalInterfaceType();
    }
    return null;
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
    if (refEntity instanceof RefProject || refEntity instanceof RefJavaModule) {
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
      }
    }

    return result;
  }

  @Override
  @Nullable
  public RefClass getOwnerClass(RefManager refManager, UElement uElement) {
    while (uElement != null && !(uElement instanceof UClass)) {
      uElement = uElement.getUastParent();
    }

    return uElement != null ? (RefClass)refManager.getReference(uElement.getSourcePsi()) : null;
  }

  @Override
  @Nullable
  public RefClass getOwnerClass(RefElement refElement) {
    RefEntity parent = refElement.getOwner();

    while (!(parent instanceof RefClass) && parent instanceof RefElement) {
      parent = parent.getOwner();
    }

    if (parent instanceof RefClass) return (RefClass)parent;

    return null;
  }


  @Override
  public boolean isMethodOnlyCallsSuper(UMethod method) {
    PsiMethod javaMethod = method.getJavaPsi();
    boolean hasStatements = false;
    UExpression body = method.getUastBody();
    if (body != null) {
      List<UExpression> statements =
        body instanceof UBlockExpression ? ((UBlockExpression)body).getExpressions() : Collections.singletonList(body);
      for (UExpression expression : statements) {
        boolean isCallToSameSuper = false;
        if (expression instanceof UReturnExpression) {
          UExpression returnExpr = ((UReturnExpression)expression).getReturnExpression();
          isCallToSameSuper = returnExpr == null || isCallToSuperMethod(returnExpr, method);
        }
        else if (!(expression instanceof UBlockExpression)) {
          isCallToSameSuper = isCallToSuperMethod(expression, method);
        }

        hasStatements = true;
        if (isCallToSameSuper) continue;
        return false;
      }
    }

    if (hasStatements) {
      final PsiMethod[] superMethods = javaMethod.findSuperMethods();
      int defaultCount = 0;
      for (PsiMethod superMethod : superMethods) {
        if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(superMethod.getModifierList()),
                                   VisibilityUtil.getVisibilityModifier(javaMethod.getModifierList())) > 0) {
          return false;
        }
        if (superMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
          defaultCount++;
        }
      }
      if (defaultCount > 1) {
        return false;
      }
    }
    return hasStatements;
  }

  @Override
  public boolean isCallToSuperMethod(UExpression expression, UMethod method) {
    if (expression instanceof UQualifiedReferenceExpression) {
      UExpression receiver = ((UQualifiedReferenceExpression)expression).getReceiver();
      UExpression selector = ((UQualifiedReferenceExpression)expression).getSelector();

      if (receiver instanceof USuperExpression && selector instanceof UCallExpression) {
        PsiMethod superMethod = ((UCallExpression)selector).resolve();
        if (superMethod == null || !MethodSignatureUtil.areSignaturesEqual(method.getJavaPsi(), superMethod)) return false;

        List<UExpression> args = ((UCallExpression)selector).getValueArguments();
        List<UParameter> params = method.getUastParameters();

        for (int i = 0; i < args.size(); i++) {
          UExpression arg = args.get(i);
          if (!(arg instanceof USimpleNameReferenceExpression)) return false;
          if (!params.get(i).equals(((USimpleNameReferenceExpression)arg).resolve())) return false;
        }

        return true;
      }
    }

    return false;
  }

  @Override
  public int compareAccess(String a1, String a2) {
    return Integer.compare(getAccessNumber(a1), getAccessNumber(a2));
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
  public void addTypeReference(UElement uElement, PsiType psiType, RefManager refManager) {
    addTypeReference(uElement, psiType, refManager, null);
  }

  @Override
  public void addTypeReference(UElement uElement, PsiType psiType, RefManager refManager, @Nullable RefJavaElement refMethod) {
    if (psiType != null) {
      final RefClass ownerClass = getOwnerClass(refManager, uElement);
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
            ((RefManagerImpl)refManager).fireNodeMarkedReferenced(psiClass, uElement.getSourcePsi());
          }
        }
      }
    }
  }

  private static boolean isAccessedForWriting(@NotNull UElement expression) {
    if (isOnAssignmentLeftHand(expression)) return true;
    UElement parent = skipParenthesises(expression);
    return isIncrementDecrement(parent);
  }

  private static boolean isIncrementDecrement(UElement element) {
    if (!(element instanceof UUnaryExpression)) return false;
    UastOperator operator = ((UUnaryExpression)element).getOperator();
    return operator == UastPostfixOperator.DEC
           || operator == UastPostfixOperator.INC
           || operator == UastPrefixOperator.DEC
           || operator == UastPrefixOperator.INC;
  }

  private static boolean isAccessedForReading(@NotNull UElement expression) {
    UElement parent = skipParenthesises(expression);
    return !(parent instanceof UBinaryExpression) ||
           !(((UBinaryExpression)parent).getOperator() instanceof UastBinaryOperator.AssignOperator) ||
           UastUtils.isUastChildOf(((UBinaryExpression)parent).getRightOperand(), expression, false);
  }

  private static boolean isOnAssignmentLeftHand(@NotNull UElement expression) {
    UExpression parent = ObjectUtils.tryCast(skipParenthesises(expression), UExpression.class);
    if (parent == null) return false;
    return parent instanceof UBinaryExpression
           && ((UBinaryExpression)parent).getOperator() instanceof UastBinaryOperator.AssignOperator
           && UastUtils.isUastChildOf(expression, ((UBinaryExpression)parent).getLeftOperand(), false);
  }

  private static UElement skipParenthesises(@NotNull UElement expression) {
    return UastUtils.skipParentOfType(expression, true, UParenthesizedExpression.class);
  }
}
