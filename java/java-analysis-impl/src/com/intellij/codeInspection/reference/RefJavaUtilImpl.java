// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RefJavaUtilImpl extends RefJavaUtil {
  private static final Logger LOG = Logger.getInstance(RefJavaUtilImpl.class);

  @Override
  public void addReferences(@NotNull PsiModifierListOwner psiFrom, @NotNull RefJavaElement ref, @Nullable PsiElement findIn) {
    UDeclaration decl = UastContextKt.toUElement(psiFrom, UDeclaration.class);
    UElement uFindIn = UastContextKt.toUElement(findIn);
    if (decl != null && findIn != null) {
      addReferencesTo(decl, ref, uFindIn);
    }
  }

  @Override
  public void addReferencesTo(@NotNull final UElement decl, @NotNull final RefJavaElement ref, final UElement @Nullable ... findIn) {
    final RefJavaElementImpl refFrom = (RefJavaElementImpl)ref;
    final RefManagerImpl refManager = refFrom.getRefManager();
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
                           final RefElement refClass = refManager.getReference(javaClass.getOriginalElement());
                           if (refClass != null) refClass.waitForInitialized();
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
                             @Override
                             public Void visitClassType(@NotNull PsiClassType classType) {
                               for (PsiType parameter : classType.getParameters()) {
                                 parameter.accept(this);
                               }
                               UClass target = UastContextKt.toUElement(classType.resolve(), UClass.class);
                               if (target != null) {
                                 final RefElement refElement = refManager.getReference(target.getSourcePsi());
                                 if (refElement != null) refElement.waitForInitialized();
                                 refFrom.addReference(refElement, target.getSourcePsi(), decl, false, true, null);
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
                           final RefElement refElement = refManager.getReference(aClass);
                           refFrom.addReference(refElement, aClass, decl, false, true, null);
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
                               final RefElement refResolved = refManager.getReference(resolve);
                               if (refResolved != null) refResolved.waitForInitialized();
                               refFrom.addReference(refResolved, resolve, decl, false, true, null);
                               if (refResolved instanceof RefMethodImpl) {
                                 updateRefMethod(resolve, (RefMethodImpl)refResolved, node, decl);
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
                                 processClassReference((PsiClass)constructorClass, true, node);
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

                       private void visitReferenceExpression(@NotNull UExpression node) {
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

                         RefElement refResolved;
                         if (psiResolved instanceof LightRecordCanonicalConstructor) {
                           refResolved = refManager.getReference(psiResolved.getNavigationElement());
                           if (refResolved instanceof RefClass) {
                             refResolved.waitForInitialized();
                             List<RefMethod> constructors = ((RefClass)refResolved).getConstructors();
                             if (!constructors.isEmpty()) {
                               refResolved = constructors.get(0);
                             }
                           }
                         }
                         else {
                           if (psiResolved instanceof LightElement) {
                             psiResolved = psiResolved.getNavigationElement();
                           }

                           refResolved = refManager.getReference(psiResolved);
                         }
                         boolean writing = isAccessedForWriting(node);
                         boolean reading = isAccessedForReading(node);
                         if (refResolved != null) refResolved.waitForInitialized();
                         refFrom.waitForInitialized();
                         refFrom.addReference(refResolved, psiResolved, decl, writing, reading, node);

                         if (refResolved instanceof RefMethodImpl) {
                           updateRefMethod(psiResolved, (RefMethodImpl)refResolved, node, decl);
                         }

                         if (psiResolved instanceof PsiMember) {
                           //TODO support kotlin
                           addClassReferenceForStaticImport(node, (PsiMember)psiResolved, refFrom, decl);
                         }
                         else {
                           // todo currently if psiResolved is KtParameter, it doesn't convert to UParameter, that seems wrong
                           UParameter uParam = UastContextKt.toUElement(psiResolved, UParameter.class);
                           if (uParam != null) {
                             addReferenceToLambdaParameter(uParam, psiResolved, decl, refFrom);
                           }
                         }
                       }

                       @Override
                       public boolean visitLambdaExpression(@NotNull ULambdaExpression node) {
                         processFunctionalExpression(node, node.getFunctionalInterfaceType());
                         return false;
                       }

                       @Override
                       public boolean visitCallableReferenceExpression(@NotNull UCallableReferenceExpression node) {
                         visitReferenceExpression(node);
                         // todo doesn't work for kotlin
                         PsiType interfaceType = getFunctionalInterfaceType(node);
                         processFunctionalExpression(node, interfaceType);
                         markParametersReferenced(node, interfaceType);
                         return false;
                       }

                        private void markParametersReferenced(@NotNull UCallableReferenceExpression node, @Nullable PsiType type) {
                          PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(type);
                          if (method == null) return;
                          for (PsiParameter param : method.getParameterList().getParameters()) {
                            RefElement paramRef = refManager.getReference(param);
                            if (paramRef != null) {
                              paramRef.waitForInitialized();
                              refFrom.addReference(paramRef, param, decl, false, true, node);
                            }
                          }
                        }

                       private void processFunctionalExpression(@NotNull UExpression expression, @Nullable PsiType type) {
                         PsiElement aClass = PsiUtil.resolveClassInType(type);
                         if (aClass != null) {
                           aClass = ((PsiClass)aClass).getSourceElement();
                         }
                         if (aClass != null) {
                           final RefElement refWhat = refManager.getReference(aClass);
                           if (refWhat != null) refWhat.waitForInitialized();
                           refFrom.addReference(refWhat, aClass, decl, false, true, null);
                           final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(type);
                           if (interfaceMethod != null) {
                             RefElement interfaceMethodRef = refManager.getReference(interfaceMethod);
                             if (interfaceMethodRef != null) interfaceMethodRef.waitForInitialized();
                             refFrom.addReference(interfaceMethodRef, interfaceMethod, decl, false, true, null);
                             refManager.fireNodeMarkedReferenced(interfaceMethod, expression.getSourcePsi());
                           }
                         }
                       }

                       @Nullable
                       private RefMethod processNewLikeConstruct(final PsiMethod javaConstructor, final List<UExpression> argumentList) {
                         if (javaConstructor == null) return null;
                         RefMethodImpl refConstructor = (RefMethodImpl)refManager.getReference(javaConstructor.getOriginalElement());
                         refFrom.addReference(refConstructor, javaConstructor, decl, false, true, null);

                         for (UExpression arg : argumentList) {
                           arg.accept(this);
                         }

                         if (refConstructor != null) {
                           refConstructor.waitForInitialized();
                           refConstructor.updateParameterValues(argumentList, javaConstructor);
                         }
                         return refConstructor;
                       }

                       @Override
                       public boolean visitClass(@NotNull UClass uClass) {
                         for (UTypeReferenceExpression type : uClass.getUastSuperTypes()) {
                           type.accept(this);
                         }
                         PsiElement sourcePsi = uClass.getSourcePsi();
                         RefElement refWhat = refManager.getReference(sourcePsi);
                         if (refWhat != null) refWhat.waitForInitialized();
                         refFrom.addReference(refWhat, sourcePsi, decl, false, true, null);
                         return true;
                       }

                       @Override
                       public boolean visitReturnExpression(@NotNull UReturnExpression node) {
                         RefMethodImpl refMethod = null;
                         if (refFrom instanceof RefMethodImpl &&
                             UastUtils.getParentOfType(node, UMethod.class, false, UClass.class, ULambdaExpression.class) == decl) {
                           refMethod = (RefMethodImpl)refFrom;
                         }
                         else if (refFrom instanceof RefFunctionalExpression) {
                           if (decl instanceof ULambdaExpression) {
                             PsiMethod lambdaMethod = LambdaUtil.getFunctionalInterfaceMethod(((ULambdaExpression)decl).getFunctionalInterfaceType());
                             refMethod = ObjectUtils.tryCast(refManager.getReference(lambdaMethod), RefMethodImpl.class);
                           }
                         }
                         if (refMethod != null) {
                           refMethod.waitForInitialized();
                           refMethod.updateReturnValueTemplate(node.getReturnExpression());
                         }
                         return false;
                       }

                       @Override
                       public boolean visitClassLiteralExpression(@NotNull UClassLiteralExpression node) {
                         final PsiType type = node.getType();
                         if (type instanceof PsiClassType) {
                           processClassReference(((PsiClassType)type).resolve(), false, node);
                         }
                         return false;
                       }

                       private void processClassReference(PsiClass psiClass, boolean defaultConstructorOnly, UExpression node) {
                         if (psiClass != null) {
                           RefClassImpl refClass =
                             ObjectUtils.tryCast(refManager.getReference(psiClass.getNavigationElement()), RefClassImpl.class);

                           if (refClass != null) {
                             boolean hasConstructorsMarked = false;
                             refClass.waitForInitialized();

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

                               UClass uClass = refClass.getUastElement();
                               if (uClass != null && uClass.getJavaPsi().isEnum()) {
                                 for (RefEntity child : refClass.getChildren()) {
                                   if (child instanceof RefFieldImpl) {
                                     UField uField = ((RefField)child).getUastElement();
                                     if (uField instanceof UEnumConstant) {
                                       ((RefFieldImpl)child).markReferenced(refFrom, false, true, node);
                                       refFrom.addOutReference((RefElement)child);
                                     }
                                   }
                                 }
                               }
                             }

                             if (!hasConstructorsMarked) {
                               refFrom.addReference(refClass, psiClass, decl, false, true, node);
                             }
                           }
                         }
                       }
                     }
      );
    }
  }

  private static void addReferenceToLambdaParameter(@NotNull UParameter uParam, @NotNull PsiElement param, @NotNull UElement decl,
                                                    @NotNull RefJavaElementImpl refFrom) {
    ULambdaExpression lambda = UastUtils.getParentOfType(uParam, ULambdaExpression.class);
    if (lambda == null) return;
    int paramIndex = -1;
    List<UParameter> lambdaParams = lambda.getParameters();
    for (int i = 0; i < lambdaParams.size(); i++) {
      if (lambdaParams.get(i).equals(uParam)) {
        paramIndex = i;
        break;
      }
    }
    if (paramIndex == -1) return;
    RefElement method = refFrom.getRefManager().getReference(LambdaUtil.getFunctionalInterfaceMethod(lambda.getFunctionalInterfaceType()));
    if (method instanceof RefMethod) {
      method.waitForInitialized();
      RefParameter[] methodParams = ((RefMethod)method).getParameters();
      refFrom.addReference(methodParams[paramIndex], param, decl, false, true, null);
    }
  }

  private static void addClassReferenceForStaticImport(UExpression node,
                                                       PsiMember psiResolved,
                                                       RefJavaElementImpl refFrom, UElement decl) {
    PsiElement sourcePsi = node.getSourcePsi();
    if (sourcePsi instanceof PsiReferenceExpression) {
      JavaResolveResult result = ((PsiReferenceExpression)sourcePsi).advancedResolve(false);
      if (result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) {
        final PsiClass containingClass = psiResolved.getContainingClass();
        if (containingClass != null) {
          RefElement refContainingClass = refFrom.getRefManager().getReference(containingClass);
          if (refContainingClass != null) {
            refContainingClass.waitForInitialized();
            refFrom.addReference(refContainingClass, containingClass, decl, false, true, node);
          }
        }
      }
    }
  }

  private static PsiElement tryFindKotlinParameter(@NotNull UExpression node, @NotNull UElement decl) {
    //TODO see KT-25524
    if (node instanceof UCallExpression && "invoke".equals(((UCallExpression)node).getMethodName())) {
      UIdentifier identifier = ((UCallExpression)node).getMethodIdentifier();
      if (identifier != null) {
        String name = identifier.getName();
        if (decl instanceof UMethod) {
          UParameter parameter = ContainerUtil.find(((UMethod)decl).getUastParameters(), p -> name.equals(p.getName()));
          if (parameter != null) {
            return parameter.getSourcePsi();
          }
        }
      }
    }
    return null;
  }

  private void updateRefMethod(PsiElement psiResolved,
                               RefMethodImpl refMethod,
                               UExpression refExpression,
                               final UElement uFrom) {
    UMethod uMethod = Objects.requireNonNull(UastContextKt.toUElement(psiResolved, UMethod.class));
    refMethod.waitForInitialized();
    if (refExpression instanceof UCallableReferenceExpression) {
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor()) {
        final PsiType type = getFunctionalInterfaceType((UCallableReferenceExpression)refExpression);
        if (!PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(type))) {
          refMethod.setReturnValueUsed(true);
          addTypeReference(uFrom, returnType, refMethod.getRefManager());
        }
      }
      refMethod.setParametersAreUnknown();
      return;
    }
    if (refExpression instanceof ULiteralExpression) { //references in literal expressions
      PsiType returnType = uMethod.getReturnType();
      if (!uMethod.isConstructor() && !PsiType.VOID.equals(returnType)) {
        refMethod.setReturnValueUsed(true);
        addTypeReference(uFrom, returnType, refMethod.getRefManager());
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

        addTypeReference(uFrom, returnType, refMethod.getRefManager());
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

  private static PsiType getFunctionalInterfaceType(@NotNull UCallableReferenceExpression expression) {
    PsiElement psi = expression.getSourcePsi();
    if (psi instanceof PsiFunctionalExpression) {
      return ((PsiFunctionalExpression)psi).getFunctionalInterfaceType();
    }
    return null;
  }

  @Override
  public RefClass getTopLevelClass(@NotNull RefElement refElement) {
    LOG.assertTrue(refElement.isInitialized(), refElement.getName() + " not initialized");
    RefEntity refParent = refElement.getOwner();

    while (refParent instanceof RefElement && !(refParent instanceof RefFile)) {
      refElement = (RefElement)refParent;
      refParent = refParent.getOwner();
    }

    return refElement instanceof RefClass ? (RefClass)refElement : null;
  }

  @Override
  public boolean isInheritor(@NotNull RefClass subClass, RefClass superClass) {
    if (subClass == superClass) return true;
    LOG.assertTrue(subClass.isInitialized());

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
    if (!(refEntity instanceof RefJavaElement) && !(refEntity instanceof RefPackage) && !(refEntity instanceof RefJavaFileImpl)) {
      return null;
    }
    RefPackage refPackage = getPackage(refEntity);

    return refPackage == null ? JavaAnalysisBundle.message("inspection.reference.default.package") : refPackage.getQualifiedName();
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

    if (uElement != null) {
      RefElement reference = refManager.getReference(uElement.getSourcePsi());
      return reference instanceof RefClass ? (RefClass)reference : null;
    }

    return null;
  }

  @Override
  @Nullable
  public RefClass getOwnerClass(RefElement refElement) {
    LOG.assertTrue(refElement.isInitialized(), refElement.getName() + " not initialized");
    RefEntity parent = refElement.getOwner();

    while (!(parent instanceof RefClass) && parent instanceof RefElement) {
      LOG.assertTrue(((RefElement)parent).isInitialized());
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
      if (statements.size() > 1) return false;
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
      for (PsiMethod superMethod : superMethods) {
        if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(superMethod.getModifierList()),
                                   VisibilityUtil.getVisibilityModifier(javaMethod.getModifierList())) > 0) {
          return false;
        }
      }
      PsiClass aClass = javaMethod.getContainingClass();
      if (aClass == null ||
          GenericsHighlightUtil.getUnrelatedDefaultsMessage(aClass, Arrays.asList(superMethods), true) != null) {
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
    UElement parent = skipParentheses(expression);
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
    UElement parent = skipParentheses(expression);
    return !(parent instanceof UBinaryExpression) ||
           !(((UBinaryExpression)parent).getOperator() instanceof UastBinaryOperator.AssignOperator) ||
           UastUtils.isUastChildOf(((UBinaryExpression)parent).getRightOperand(), expression, false);
  }

  private static boolean isOnAssignmentLeftHand(@NotNull UElement expression) {
    UExpression parent = ObjectUtils.tryCast(skipParentheses(expression), UExpression.class);
    if (parent == null) return false;
    return parent instanceof UBinaryExpression
           && ((UBinaryExpression)parent).getOperator() instanceof UastBinaryOperator.AssignOperator
           && UastUtils.isUastChildOf(expression, ((UBinaryExpression)parent).getLeftOperand(), false);
  }

  private static UElement skipParentheses(@NotNull UElement expression) {
    return UastUtils.skipParentOfType(expression, true, UParenthesizedExpression.class);
  }
}
