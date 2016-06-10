/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree.*;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubs.StubTreeBuilder;
import com.intellij.psi.stubsHierarchy.StubHierarchyIndexer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaStubIndexer extends StubHierarchyIndexer {

  @Override
  public int getVersion() {
    return 0;
  }

  @Override
  public boolean handlesFile(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    return fileType == JavaFileType.INSTANCE || fileType == JavaClassFileType.INSTANCE;
  }

  @Nullable
  @Override
  public List<Pair<String, Unit>> indexFile(@NotNull FileContent content) {
    Stub stubTree = StubTreeBuilder.buildStubTree(content);
    if (!(stubTree instanceof PsiJavaFileStub)) return null;

    PsiJavaFileStub javaFileStub = (PsiJavaFileStub)stubTree;
    new StubTree(javaFileStub, false);

    ArrayList<ClassDecl> classList = new ArrayList<ClassDecl>();
    Set<String> usedNames = new HashSet<String>();
    for (StubElement<?> el : javaFileStub.getChildrenStubs()) {
      if (el instanceof PsiClassStubImpl) {
        ClassDecl classDecl = processClassDecl((PsiClassStubImpl<?>)el, usedNames);
        if (classDecl != null) {
          classList.add(classDecl);
        }
      }
    }
    ArrayList<Import> importList = new ArrayList<Import>();
    for (StubElement<?> el : javaFileStub.getChildrenStubs()) {
      if (el instanceof PsiImportListStub) {
        processImport((PsiImportListStub) el, importList, usedNames);
      }
    }
    ClassDecl[] classes = classList.isEmpty() ? ClassDecl.EMPTY_ARRAY : classList.toArray(new ClassDecl[classList.size()]);
    Import[] imports = importList.isEmpty() ? Import.EMPTY_ARRAY : importList.toArray(new Import[importList.size()]);
    byte type = javaFileStub.isCompiled() ? IndexTree.BYTECODE : IndexTree.JAVA;
    return Collections.singletonList(Pair.create(javaFileStub.getPackageName(), new Unit(type, imports, classes)));
  }

  @Nullable
  private static Decl processMember(StubElement<?> el, Set<String> namesCache) {
    if (el instanceof PsiClassStubImpl) {
      PsiClassStubImpl classStub = (PsiClassStubImpl)el;
      if (!classStub.isAnonymousInQualifiedNew()) {
        return processClassDecl(classStub, namesCache);
      }
    }
    ArrayList<Decl> innerList = new ArrayList<Decl>();
    for (StubElement childElement : el.getChildrenStubs()) {
      Decl innerDef = processMember(childElement, namesCache);
      if (innerDef != null) {
        innerList.add(innerDef);
      }
    }
    return innerList.isEmpty() ? null : new MemberDecl(innerList.toArray(new Decl[innerList.size()]));
  }

  @Nullable
  private static ClassDecl processClassDecl(PsiClassStubImpl<?> classStub, Set<String> namesCache) {
    ArrayList<String> superList = new ArrayList<String>();
    ArrayList<Decl> innerList = new ArrayList<Decl>();
    int accessModifiers = 0;
    if (classStub.isAnonymous()) {
      if (classStub.getBaseClassReferenceText() != null) {
        superList.add(id(classStub.getBaseClassReferenceText(), true, namesCache));
      }
    }
    for (StubElement el : classStub.getChildrenStubs()) {
      if (el instanceof PsiClassReferenceListStub) {
        PsiClassReferenceListStub refList = (PsiClassReferenceListStub)el;
        if (refList.getRole() == PsiReferenceList.Role.EXTENDS_LIST) {
          String[] extendNames = refList.getReferencedNames();
          for (String extName : extendNames) {
            superList.add(id(extName, true, namesCache));
          }
        }
        if (refList.getRole() == PsiReferenceList.Role.IMPLEMENTS_LIST) {
          String[] implementNames = refList.getReferencedNames();
          for (String impName : implementNames) {
            superList.add(id(impName, true, namesCache));
          }
        }
      }
      if (el instanceof PsiModifierListStub) {
        accessModifiers = ((PsiModifierListStub)el).getModifiersMask();
      }
      Decl member = processMember(el, namesCache);
      if (member != null) {
        innerList.add(member);
      }

    }
    int flags = translateFlags(classStub, accessModifiers);
    String[] supers = superList.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : ArrayUtil.toStringArray(superList);
    Decl[] inners = innerList.isEmpty() ? Decl.EMPTY_ARRAY : innerList.toArray(new Decl[innerList.size()]);
    return new ClassDecl(classStub.id, flags, classStub.getName(), supers, inners);
  }

  private static int translateFlags(PsiClassStubImpl<?> classStub, int accessModifiers) {
    int flags = 0;
    if (classStub.isInterface()) {
      flags |= IndexTree.INTERFACE;
    }
    if (classStub.isEnum()) {
      flags |= IndexTree.ENUM;
    }
    if (classStub.isAnnotationType()) {
      flags |= IndexTree.ANNOTATION;
    }
    if (ModifierFlags.hasModifierProperty(PsiModifier.STATIC, accessModifiers)) {
      flags |= IndexTree.STATIC;
    }
    return flags;
  }

  private static void processImport(PsiImportListStub el, List<Import> imports, Set<String> namesCache) {
    for (StubElement<?> importElem : el.getChildrenStubs()) {
      PsiImportStatementStub imp = (PsiImportStatementStub)importElem;
      String importReferenceText = imp.getImportReferenceText();
      if (importReferenceText != null) {
        String fullName = PsiNameHelper.getQualifiedClassName(importReferenceText, true);
        if (imp.isOnDemand() || namesCache.contains(shortName(fullName))) {
          imports.add(new Import(fullName, imp.isStatic(), imp.isOnDemand(), null));
        }
      }
    }
  }

  private static String id(String s, boolean cacheFirstId, Set<String> namesCache) {
    String id = PsiNameHelper.getQualifiedClassName(s, true);
    if (cacheFirstId) {
      int index = id.indexOf('.');
      String firstId = index > 0 ? s.substring(0, index) : id;
      namesCache.add(firstId);
    }
    return id;
  }

  private static String shortName(String s) {
    int dotIndex = s.lastIndexOf('.');
    return dotIndex > 0 ? s.substring(dotIndex + 1) : null;
  }
}
