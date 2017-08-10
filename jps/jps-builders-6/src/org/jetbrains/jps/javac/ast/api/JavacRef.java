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
package org.jetbrains.jps.javac.ast.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Set;

public interface JavacRef {
  JavacRef[] EMPTY_ARRAY = new JavacRef[0];

  @NotNull
  String getName();

  Set<Modifier> getModifiers();

  @NotNull
  String getOwnerName();

  interface JavacClass extends JavacRef {
    boolean isAnonymous();
  }

  interface JavacMethod extends JavacRef {
    byte getParamCount();
  }

  interface JavacField extends JavacRef {
  }

  abstract class JavacRefBase implements JavacRef {
    private final String myName;
    private final Set<Modifier> myModifiers;

    protected JavacRefBase(String name, Set<Modifier> modifiers) {
      myName = name;
      myModifiers = modifiers;
    }

    @NotNull
    @Override
    public final String getName() {
      return myName;
    }

    @Override
    public final Set<Modifier> getModifiers() {
      return myModifiers;
    }
  }

  class JavacClassImpl extends JavacRefBase implements JavacClass {
    private boolean myAnonymous;

    public JavacClassImpl(boolean anonymous, Set<Modifier> modifiers, String name) {
      super(name, modifiers);
      myAnonymous = anonymous;
    }

    @NotNull
    @Override
    public String getOwnerName() {
      throw new UnsupportedOperationException();
    }

    public boolean isAnonymous() {
      return myAnonymous;
    }
  }

  class JavacMethodImpl extends JavacRefBase implements JavacMethod {
    private final String myOwnerName;
    private final byte myParamCount;

    public JavacMethodImpl(String ownerName, byte paramCount, Set<Modifier> modifiers, String name) {
      super(name, modifiers);
      myOwnerName = ownerName;
      myParamCount = paramCount;
    }

    public byte getParamCount() {
      return myParamCount;
    }

    @NotNull
    @Override
    public String getOwnerName() {
      return myOwnerName;
    }
  }

  class JavacFieldImpl extends JavacRefBase implements JavacField {
    private final String myOwnerName;

    public JavacFieldImpl(String ownerName, Set<Modifier> modifiers, String name) {
      super(name, modifiers);
      myOwnerName = ownerName;
    }

    @NotNull
    @Override
    public String getOwnerName() {
      return myOwnerName;
    }
  }

  abstract class JavacElementRefBase implements JavacRef {
    protected final @NotNull Element myOriginalElement;
    @Nullable private final Element myQualifier;
    protected final JavacNameTable myNameTableCache;

    protected JavacElementRefBase(@NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache) {
      myOriginalElement = element;
      myQualifier = qualifier;
      myNameTableCache = nameTableCache;
    }

    @NotNull
    @Override
    public String getName() {
      return myNameTableCache.parseName(myOriginalElement.getSimpleName());
    }

    @Override
    public Set<Modifier> getModifiers() {
      return myOriginalElement.getModifiers();
    }

    @NotNull
    @Override
    public String getOwnerName() {
      return myNameTableCache.parseBinaryName(myQualifier != null ? myQualifier : myOriginalElement.getEnclosingElement());
    }

    @Nullable
    public static JavacElementRefBase fromElement(Element element, Element qualifier, JavacNameTable nameTableCache) {
      if (qualifier != null) {
        TypeMirror type = qualifier.asType();
        if (!isValidType(type)) {
          return null;
        }
      }
      if (element instanceof TypeElement) {
        return new JavacElementClassImpl(element, qualifier, nameTableCache);
      }
      else if (element instanceof VariableElement) {
        if (qualifier == null && !checkEnclosingElement(element)) return null;
        return new JavacElementFieldImpl(element, qualifier, nameTableCache);
      }
      else if (element instanceof ExecutableElement) {
        if (qualifier == null && !checkEnclosingElement(element)) return null;
        return new JavacElementMethodImpl(element, qualifier, nameTableCache);
      }
      else if (element == null || element.getKind() == ElementKind.OTHER || element.getKind() == ElementKind.TYPE_PARAMETER) {
        // javac reserved symbol kind (e.g: com.sun.tools.javac.comp.Resolve.ResolveError)
        return null;
      }
      throw new AssertionError("unexpected element: " + element + " class: " + element.getClass());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavacElementRefBase base = (JavacElementRefBase)o;

      return myOriginalElement == base.myOriginalElement && myQualifier == base.myQualifier;
    }

    @Override
    public int hashCode() {
      int hashCode = myOriginalElement.hashCode();
      if (myQualifier != null) {
        hashCode += myQualifier.hashCode();
      }
      return hashCode;
    }

    private static boolean checkEnclosingElement(Element element) {
      Element enclosingElement = element.getEnclosingElement();
      if (enclosingElement == null) return false;
      TypeMirror type = enclosingElement.asType();
      if (!isValidType(type)) {
        return false;
      }
      return true;
    }

    private static boolean isValidType(TypeMirror type) {
      return type != null && type.getKind() != TypeKind.NONE && type.getKind() != TypeKind.OTHER;
    }
  }

  class JavacElementClassImpl extends JavacElementRefBase implements JavacClass {
   public JavacElementClassImpl(@NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache) {
      super(element, qualifier, nameTableCache);
    }

    @NotNull
    @Override
    public String getName() {
      return myNameTableCache.parseBinaryName(myOriginalElement);
    }

    @Override
    public boolean isAnonymous() {
      return myNameTableCache.parseName(myOriginalElement.getSimpleName()).isEmpty();
    }
  }

  class JavacElementMethodImpl extends JavacElementRefBase implements JavacMethod {
    public JavacElementMethodImpl(@NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache) {
      super(element, qualifier, nameTableCache);
    }

    @Override
    public byte getParamCount() {
      return (byte)((ExecutableElement)myOriginalElement).getParameters().size();
    }
  }

  class JavacElementFieldImpl extends JavacElementRefBase implements JavacField {
    public JavacElementFieldImpl(@NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache) {
      super(element, qualifier, nameTableCache);
    }
  }
}
