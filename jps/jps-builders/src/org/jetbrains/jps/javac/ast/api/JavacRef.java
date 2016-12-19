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
package org.jetbrains.jps.javac.ast.api;

import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.*;
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
    protected final JavacNameTable myNameTableCache;

    protected JavacElementRefBase(@NotNull Element element, JavacNameTable nameTableCache) {
      myOriginalElement = element;
      myNameTableCache = nameTableCache;
    }

    @NotNull
    public Element getOriginalElement() {
      return myOriginalElement;
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
      return myNameTableCache.parseName(myNameTableCache.getBinaryName(myOriginalElement.getEnclosingElement()));
    }

    public static JavacElementRefBase fromElement(Element element, JavacNameTable nameTableCache) {
      if (element instanceof TypeElement) {
        return new JavacElementClassImpl(element, nameTableCache);
      }
      else if (element instanceof VariableElement) {
        return new JavacElementFieldImpl(element, nameTableCache);
      }
      else if (element instanceof ExecutableElement) {
        return new JavacElementMethodImpl(element, nameTableCache);
      }
      throw new AssertionError("unexpected element: " + element + " class: " + element.getClass());
    }
  }

  class JavacElementClassImpl extends JavacElementRefBase implements JavacClass {
   public JavacElementClassImpl(@NotNull Element element, JavacNameTable nameTableCache) {
      super(element, nameTableCache);
    }

    @NotNull
    @Override
    public String getName() {
      return myNameTableCache.parseName(myNameTableCache.getBinaryName(myOriginalElement));
    }

    @Override
    public boolean isAnonymous() {
      return myNameTableCache.parseName(myOriginalElement.getSimpleName()).isEmpty();
    }
  }

  class JavacElementMethodImpl extends JavacElementRefBase implements JavacMethod {
    public JavacElementMethodImpl(@NotNull Element element, JavacNameTable nameTableCache) {
      super(element, nameTableCache);
    }

    @Override
    public byte getParamCount() {
      return (byte)((ExecutableElement)myOriginalElement).getParameters().size();
    }
  }

  class JavacElementFieldImpl extends JavacElementRefBase implements JavacField {
    public JavacElementFieldImpl(@NotNull Element element, JavacNameTable nameTableCache) {
      super(element, nameTableCache);
    }
  }
}
