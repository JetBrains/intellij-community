// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stub.JavaStubImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class PsiClassImpl extends JavaStubPsiElement<PsiClassStub<?>> implements PsiExtensibleClass, Queryable {
  private final ClassInnerStuffCache myInnersCache = new ClassInnerStuffCache(this);
  private volatile String myCachedName;

  public PsiClassImpl(final PsiClassStub stub) {
    this(stub, JavaStubElementTypes.CLASS);
  }

  protected PsiClassImpl(final PsiClassStub stub, final IStubElementType type) {
    super(stub, type);
    addTrace(null);
  }

  public PsiClassImpl(final ASTNode node) {
    super(node);
    addTrace(null);
  }

  private void addTrace(@Nullable PsiClassStub stub) {
    if (ourTraceStubAstBinding) {
      String creationTrace = "Creation thread: " + Thread.currentThread() + "\n" + DebugUtil.currentStackTrace();
      if (stub != null) {
        creationTrace += "\nfrom stub " + stub + "@" + System.identityHashCode(stub) + "\n";
        if (stub instanceof UserDataHolder) {
          String stubTrace = ((UserDataHolder)stub).getUserData(CREATION_TRACE);
          if (stubTrace != null) {
            creationTrace += stubTrace;
          }
        }
      }
      putUserData(CREATION_TRACE, creationTrace);
    }
  }

  @Override
  public void subtreeChanged() {
    dropCaches();
    super.subtreeChanged();
  }

  private void dropCaches() {
    myCachedName = null;
  }

  @Override
  protected Object clone() {
    PsiClassImpl clone = (PsiClassImpl)super.clone();
    clone.dropCaches();
    return clone;
  }

  @Override
  public PsiElement getOriginalElement() {
    if (DumbService.isDumb(getProject())) {
      // Avoid caching in dumb mode, as JavaPsiImplementationHelper.getOriginalClass depends on it
      return this;
    }
    return CachedValuesManager.getCachedValue(this, () -> {
      final JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(getProject());
      final PsiClass result = helper != null ? helper.getOriginalClass(this) : this;
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
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

  @Override
  public String getName() {
    String name = myCachedName;
    if (name != null) return name;
    final PsiClassStub stub = getGreenStub();
    if (stub == null) {
      PsiIdentifier identifier = getNameIdentifier();
      name = identifier == null ? null : identifier.getText();
    }
    else {
      name = stub.getName();
    }
    myCachedName = name;
    return name;
  }

  @Override
  public String getQualifiedName() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }

    PsiElement parent = getParent();
    if (parent instanceof PsiJavaFile) {
      return StringUtil.getQualifiedName(((PsiJavaFile)parent).getPackageName(), StringUtil.notNullize(getName()));
    }
    if (parent instanceof PsiClass) {
      String parentQName = ((PsiClass)parent).getQualifiedName();
      if (parentQName == null) return null;
      return StringUtil.getQualifiedName(parentQName, StringUtil.notNullize(getName()));
    }

    return null;
  }

  @Override
  public PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    final PsiModifierList modlist = getModifierList();
    return modlist != null && modlist.hasModifierProperty(name);
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return getStubOrPsiChild(JavaStubElementTypes.EXTENDS_LIST);
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return getStubOrPsiChild(JavaStubElementTypes.IMPLEMENTS_LIST);
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  public @Nullable PsiReferenceList getPermitsList() {
    return getStubOrPsiChild(JavaStubElementTypes.PERMITS_LIST);
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  public PsiClass @NotNull [] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      StubElement parent = stub.getParentStub();
      return parent instanceof PsiClassStub ? ((PsiClassStub<?>)parent).getPsi() : null;
    }

    PsiElement parent = getParent();

    if (parent instanceof PsiClassLevelDeclarationStatement) {
      return PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
    }

    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  public PsiElement getContext() {
    return getContext(null);
  }

  public PsiElement getContext(@Nullable String referenceName) {
    PsiClassStub<?> stub = getStub();
    if (stub == null) return getParent();

    // if AST is not loaded, then we only can need context to resolve class references, which specify the name
    if (referenceName == null) return super.getContext();
    
    // class names can be resolved by stubs unless this is a local/anonymous class referencing other local classes
    StubElement<?> parent = stub.getParentStub();
    while (parent != null && !(parent instanceof PsiClassStub) && !(parent instanceof PsiFileStub)) {
      PsiClass[] allLocalClasses = parent.getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
      if (allLocalClasses.length > 0 && ContainerUtil.exists(allLocalClasses, c -> referenceName.equals(c.getName()))) {
        return getParent();
      }
      if (parent instanceof PsiMethodStub) {
        return parent.getPsi();
      }
      parent = parent.getParentStub();
    }
    return parent != null ? parent.getPsi() : getParent();
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiField @NotNull [] getFields() {
    return myInnersCache.getFields();
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return myInnersCache.getMethods();
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return myInnersCache.getConstructors();
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return myInnersCache.getInnerClasses();
  }

  @Override
  public PsiRecordComponent @NotNull [] getRecordComponents() {
    return myInnersCache.getRecordComponents();
  }

  @Override
  @Nullable
  public PsiRecordHeader getRecordHeader() {
    return getStubOrPsiChild(JavaStubElementTypes.RECORD_HEADER);
  }

  @NotNull
  @Override
  public List<PsiField> getOwnFields() {
    return Arrays.asList(getStubOrPsiChildren(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY));
  }

  @NotNull
  @Override
  public List<PsiMethod> getOwnMethods() {
    return Arrays.asList(getStubOrPsiChildren(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY));
  }

  @NotNull
  @Override
  public List<PsiClass> getOwnInnerClasses() {
    return Arrays.asList(getStubOrPsiChildren(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY));
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return getStubOrPsiChildren(JavaStubElementTypes.CLASS_INITIALIZER, PsiClassInitializer.ARRAY_FACTORY);
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return myInnersCache.findFieldByName(name, checkBases);
  }

  @Override
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(String name, boolean checkBases) {
    return myInnersCache.findMethodsByName(name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return myInnersCache.findInnerClassByName(name, checkBases);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  public boolean isDeprecated() {
    return JavaStubImplUtil.isMemberDeprecated(this, getGreenStub());
  }

  @Override
  public PsiDocComment getDocComment(){
    PsiClassStub<?> stub = getGreenStub();
    if (stub != null && !stub.hasDocComment()) return null;

    return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  @Override
  public PsiJavaToken getLBrace() {
    return (PsiJavaToken)getNode().findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  @Override
  public PsiJavaToken getRBrace() {
    return (PsiJavaToken)getNode().findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  @Override
  public boolean isInterface() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isInterface();
    }

    ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.INTERFACE_KEYWORD;
  }

  @Override
  public boolean isAnnotationType() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isAnnotationType();
    }

    return getNode().findChildByRole(ChildRole.AT) != null;
  }

  @Override
  public boolean isEnum() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isEnum();
    }

    final ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.ENUM_KEYWORD;
  }

  @Override
  public boolean isRecord() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isRecord();
    }

    final ASTNode keyword = getNode().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == JavaTokenType.RECORD_KEYWORD;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString(){
    return "PsiClass:" + getName();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    LanguageLevel level = PsiUtil.getLanguageLevel(place);
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
  }

  @Override
  public PsiElement setName(@NotNull String newName) throws IncorrectOperationException{
    String oldName = getName();
    boolean isRenameFile = isRenameFileOnRenaming();

    PsiImplUtil.setName(Objects.requireNonNull(getNameIdentifier()), newName);

    if (isRenameFile) {
      PsiFile file = (PsiFile)getParent();
      String fileName = file.getName();
      int dotIndex = fileName.lastIndexOf('.');
      file.setName(dotIndex >= 0 ? newName + "." + fileName.substring(dotIndex + 1) : newName);
    }

    // rename constructors
    for (PsiMethod method : getMethods()) {
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

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    putInfo(this, info);
  }

  public static void putInfo(@NotNull PsiClass psiClass, @NotNull Map<? super String, ? super String> info) {
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

  @Nullable
  public PsiMethod getValuesMethod() {
    return myInnersCache.getValuesMethod();
  }
}