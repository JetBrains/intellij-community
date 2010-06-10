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
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaClassElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PsiClassImpl extends JavaStubPsiElement<PsiClassStub<?>> implements PsiClass, PsiQualifiedNamedElement, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiClassImpl");

  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);

  private PsiMethod myValuesMethod = null;
  private PsiMethod myValueOfMethod = null;
  private volatile String myCachedForLongName = null;
  @NonNls private static final String VALUES_METHOD = "values";
  @NonNls private static final String VALUE_OF_METHOD = "valueOf";

  public PsiClassImpl(final PsiClassStub stub) {
    this(stub, typeForClass(stub.isAnonymous(), stub.isEnumConstantInitializer()));
  }

  protected PsiClassImpl(final PsiClassStub stub, final IStubElementType type) {
    super(stub, type);
  }

  public PsiClassImpl(final ASTNode node) {
    super(node);
  }

  public static JavaClassElementType typeForClass(final boolean anonymous, final boolean enumConst) {
    return enumConst
           ? JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER
           : anonymous ? JavaStubElementTypes.ANONYMOUS_CLASS : JavaStubElementTypes.CLASS;
  }

  public void subtreeChanged() {
    dropCaches();

    super.subtreeChanged();
  }

  private void dropCaches() {
    myInnersCache.dropCaches();
    myCachedForLongName = null;
  }

  protected Object clone() {
    PsiClassImpl clone = (PsiClassImpl)super.clone();

    clone.dropCaches();

    return clone;
  }

  public PsiElement getParent() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof PsiFileStub || parentStub instanceof PsiClassStub
        ) {
        return parentStub.getPsi();
      }
    }

    return SharedImplUtil.getParent(getNode());
  }

  public PsiElement getOriginalElement() {
    PsiFile psiFile = getContainingFile();

    VirtualFile vFile = psiFile.getVirtualFile();
    final ProjectFileIndex idx = ProjectRootManager.getInstance(getProject()).getFileIndex();

    if (vFile == null || !idx.isInLibrarySource(vFile)) return this;
    final List<OrderEntry> orderEntries = idx.getOrderEntriesForFile(vFile);
    final String fqn = getQualifiedName();
    if (fqn == null) return this;

    PsiClass original = JavaPsiFacade.getInstance(getProject()).findClass(fqn, new GlobalSearchScope(getProject()) {
      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      public boolean contains(VirtualFile file) {
        // order for file and vFile has non empty intersection.
        List<OrderEntry> entries = idx.getOrderEntriesForFile(file);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < entries.size(); i++) {
          final OrderEntry entry = entries.get(i);
          if (orderEntries.contains(entry)) return true;
        }
        return false;
      }

      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return false;
      }

      public boolean isSearchInLibraries() {
        return true;
      }
    });

    return original != null ? original : this;
  }

  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public PsiElement getScope() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.getParentStub().getPsi();
    }

    ASTNode treeElement = getNode();
    ASTNode parent = treeElement.getTreeParent();

    while(parent != null) {
      if (parent.getElementType() instanceof IStubElementType){
        return parent.getPsi();
      }
      parent = parent.getTreeParent();
    }

    return getContainingFile();
  }

  public String getName() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }

    PsiIdentifier identifier = getNameIdentifier();
    return identifier != null ? identifier.getText() : null;
  }

  public String getQualifiedName() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    PsiElement parent = getParent();
    if (parent instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)parent).getPackageName();
      if (packageName.length() > 0) {
        return packageName + "." + getName();
      }
      else {
        return getName();
      }
    }
    else if (parent instanceof PsiClass) {
      String parentQName = ((PsiClass)parent).getQualifiedName();
      if (parentQName == null) return null;
      return parentQName + "." + getName();
    }

    return null;
  }

  public PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    final PsiModifierList modlist = getModifierList();
    return modlist != null && modlist.hasModifierProperty(name);
  }

  public PsiReferenceList getExtendsList() {
    return getStubOrPsiChild(JavaStubElementTypes.EXTENDS_LIST);
  }

  public PsiReferenceList getImplementsList() {
    return getStubOrPsiChild(JavaStubElementTypes.IMPLEMENTS_LIST);
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @NotNull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Nullable
  public PsiClass getContainingClass() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      StubElement parent = stub.getParentStub();
      while (parent != null && !(parent instanceof PsiClassStub)) {
        parent = parent.getParentStub();
      }

      if (parent != null) {
        return ((PsiClassStub<? extends PsiClass>)parent).getPsi();
      }
    }

    PsiElement parent = getParent();

    if (parent instanceof JspClassLevelDeclarationStatement) {
      return PsiTreeUtil.getParentOfType(this, JspClass.class);
    }

    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }


  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @NotNull
  public PsiField[] getFields() {
    return myInnersCache.getFields();
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return myInnersCache.getMethods();
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return myInnersCache.getConstructors();
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return getStubOrPsiChildren(JavaStubElementTypes.CLASS, ARRAY_FACTORY);
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return getStubOrPsiChildren(JavaStubElementTypes.CLASS_INITIALIZER, PsiClassInitializer.ARRAY_FACTORY);
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    return myInnersCache.findFieldByName(name, checkBases);
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return myInnersCache.findInnerClassByName(name, checkBases);
  }

  public PsiTypeParameterList getTypeParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  public boolean isDeprecated() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  public PsiDocComment getDocComment(){
    return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public PsiJavaToken getLBrace() {
    return (PsiJavaToken)getNode().findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  public PsiJavaToken getRBrace() {
    return (PsiJavaToken)getNode().findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  public boolean isInterface() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.isInterface();
    }

    ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.INTERFACE_KEYWORD;
  }

  public boolean isAnnotationType() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.isAnnotationType();
    }

    return getNode().findChildByRole(ChildRole.AT) != null;
  }

  public boolean isEnum() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.isEnum();
    }

    final ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.ENUM_KEYWORD;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiClass:" + getName();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (isEnum()) {
      String name = getName();
      if (name != null) {
        try {
          if (myValuesMethod == null || myValueOfMethod == null || !name.equals(myCachedForLongName)) {
            myCachedForLongName = name;
            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
            final PsiMethod valuesMethod = elementFactory.createMethodFromText("public static " + name + "[] values() {}", this);
            myValuesMethod = new LightMethod(getManager(), valuesMethod, this);
            final PsiMethod valueOfMethod = elementFactory.createMethodFromText("public static " + name + " valueOf(String name) throws IllegalArgumentException {}", this);
            myValueOfMethod = new LightMethod(getManager(), valueOfMethod, this);
          }
          final NameHint nameHint = processor.getHint(NameHint.KEY);
          final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
          if (nameHint == null || VALUES_METHOD.equals(nameHint.getName(state))) {
            if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclaractionKind.METHOD)) {
              if (!processor.execute(myValuesMethod, ResolveState.initial())) return false;
            }
          }
          if (nameHint == null || VALUE_OF_METHOD.equals(nameHint.getName(state))) {
            if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclaractionKind.METHOD)) {
              if (!processor.execute(myValueOfMethod, ResolveState.initial())) return false;
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, false);
  }

  public PsiElement setName(@NotNull String newName) throws IncorrectOperationException{
    String oldName = getName();
    boolean isRenameFile = isRenameFileOnRenaming();

    PsiImplUtil.setName(getNameIdentifier(), newName);

    if (isRenameFile) {
      PsiFile file = (PsiFile)getParent();
      String fileName = file.getName();
      int dotIndex = fileName.lastIndexOf('.');
      file.setName(dotIndex >= 0 ? newName + "." + fileName.substring(dotIndex + 1) : newName);
    }

    // rename constructors
    PsiMethod[] methods = getMethods();
    for (PsiMethod method : methods) {
      if (method.isConstructor() && method.getName().equals(oldName)) {
        method.setName(newName);
      }
    }

    return this;
  }

  private boolean isRenameFileOnRenaming() {
    final PsiElement parent = getParent();
    if (parent instanceof PsiFile) {
      PsiFile file = (PsiFile)parent;
      String fileName = file.getName();
      int dotIndex = fileName.lastIndexOf('.');
      String name = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
      String oldName = getName();
      return name.equals(oldName);
    }
    else {
      return false;
    }
  }

  // optimization to not load tree when resolving bases of anonymous and locals
  // if there is no local classes with such name in scope it's possible to use outer scope as context
  @Nullable
  public PsiElement calcBasesResolveContext(String baseClassName, final PsiElement defaultResolveContext) {
    return calcBasesResolveContext(this, baseClassName, true, defaultResolveContext);
  }

  private static boolean isAnonymousOrLocal(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return true;

    final PsiClassStub stub = ((PsiClassImpl)aClass).getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      return !(parentStub instanceof PsiClassStub || parentStub instanceof PsiFileStub);
    }

    PsiElement parent = aClass.getParent();
    while (parent != null) {
      if (parent instanceof PsiMethod || parent instanceof PsiField || parent instanceof PsiClassInitializer) return true;
      if (parent instanceof PsiClass || parent instanceof PsiFile) return false;
      parent = parent.getParent();
    }

    return false;
  }

  @Nullable
  private static PsiElement calcBasesResolveContext(PsiClass aClass,
                                                    String className,
                                                    boolean isInitialClass,
                                                    final PsiElement defaultResolveContext) {
    final PsiClassStub stub = ((PsiClassImpl)aClass).getStub();
    if (stub == null || stub.isAnonymousInQualifiedNew()) {
      return aClass.getParent();
    }

    boolean isAnonOrLocal = isAnonymousOrLocal(aClass);

    if (!isAnonOrLocal) {
      return isInitialClass ? defaultResolveContext : aClass;
    }

    if (!isInitialClass) {
      if (aClass.findInnerClassByName(className, true) != null) return aClass;
    }

    final StubElement parentStub = stub.getParentStub();

    final StubBasedPsiElementBase<?> context = (StubBasedPsiElementBase)parentStub.getPsi();
    PsiClass[] classesInScope = (PsiClass[])parentStub.getChildrenByType(Constants.CLASS_BIT_SET, ARRAY_FACTORY);

    boolean needPreciseContext = false;
    if (classesInScope.length > 1) {
      for (PsiClass scopeClass : classesInScope) {
        if (scopeClass == aClass) continue;
        String className1 = scopeClass.getName();
        if (className.equals(className1)) {
          needPreciseContext = true;
          break;
        }
      }
    }
    else {
      if (classesInScope.length != 1) {
        LOG.assertTrue(classesInScope.length == 1, "Parent stub: "+parentStub.getStubType() +"; children: "+parentStub.getChildrenStubs()+"; \ntext:"+context.getText());
      }
      LOG.assertTrue(classesInScope[0] == aClass);
    }

    if (needPreciseContext) {
      return aClass.getParent();
    }
    else {
      if (context instanceof PsiClass) {
        return calcBasesResolveContext((PsiClass)context, className, false, defaultResolveContext);
      }
      else if (context instanceof PsiMember) {
        return calcBasesResolveContext(((PsiMember)context).getContainingClass(), className, false, defaultResolveContext);
      }
      else {
        LOG.assertTrue(false);
        return context;
      }
    }
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  public ItemPresentation getPresentation() {
    return ClassPresentationUtil.getPresentation(this);
  }

  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Nullable
  public PsiQualifiedNamedElement getContainer() {
    final PsiFile file = getContainingFile();
    final PsiDirectory dir;
    return file == null ? null : (dir = file.getContainingDirectory()) == null
                                 ? null : JavaDirectoryService.getInstance().getPackage(dir);
  }

  public void putInfo(Map<String, String> info) {
    putInfo(this, info);
  }

  public static void putInfo(PsiClass psiClass, Map<String, String> info) {
    info.put("className", psiClass.getName());
    info.put("qualifiedClassName", psiClass.getQualifiedName());
    PsiFile file = psiClass.getContainingFile();
    if (file instanceof Queryable) {
      ((Queryable)file).putInfo(info);
    }
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

}
