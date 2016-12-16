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

import com.sun.tools.javac.util.Name;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.util.Set;

public interface JavacRef {
  JavacRef[] EMPTY_ARRAY = new JavacRef[0];

  @NotNull
  byte[] getName();

  Set<Modifier> getModifiers();

  @NotNull
  byte[] getOwnerName();

  interface JavacClass extends JavacRef {
    boolean isAnonymous();
  }

  interface JavacMethod extends JavacRef {
    byte getParamCount();
  }

  interface JavacField extends JavacRef {
  }

  abstract class JavacRefBase implements JavacRef {
    private final byte[] myName;
    private final Set<Modifier> myModifiers;

    protected JavacRefBase(byte[] name, Set<Modifier> modifiers) {
      myName = name;
      myModifiers = modifiers;
    }

    @NotNull
    @Override
    public final byte[] getName() {
      return myName;
    }

    @Override
    public final Set<Modifier> getModifiers() {
      return myModifiers;
    }
  }

  class JavacClassImpl extends JavacRefBase implements JavacClass {
    private boolean myAnonymous;

    public JavacClassImpl(boolean anonymous, Set<Modifier> modifiers, byte[] name) {
      super(name, modifiers);
      myAnonymous = anonymous;
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      throw new UnsupportedOperationException();
    }

    public boolean isAnonymous() {
      return myAnonymous;
    }
  }

  class JavacMethodImpl extends JavacRefBase implements JavacMethod {
    private final byte[] myOwnerName;
    private final byte myParamCount;

    public JavacMethodImpl(byte[] ownerName, byte paramCount, Set<Modifier> modifiers, byte[] name) {
      super(name, modifiers);
      myOwnerName = ownerName;
      myParamCount = paramCount;
    }

    public byte getParamCount() {
      return myParamCount;
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      return myOwnerName;
    }
  }

  class JavacFieldImpl extends JavacRefBase implements JavacField {
    private final byte[] myOwnerName;

    public JavacFieldImpl(byte[] ownerName, Set<Modifier> modifiers, byte[] name) {
      super(name, modifiers);
      myOwnerName = ownerName;
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      return myOwnerName;
    }
  }

  abstract class JavacElementRefBase implements JavacRef {
    protected final @NotNull Element myOriginalElement;
    protected final Elements myElementUtility;

    protected JavacElementRefBase(@NotNull Element element, Elements elementUtility) {
      myOriginalElement = element;
      myElementUtility = elementUtility;
    }

    @NotNull
    public Element getOriginalElement() {
      return myOriginalElement;
    }

    @NotNull
    @Override
    public byte[] getName() {
      return ((Name) myOriginalElement.getSimpleName()).toUtf();
    }

    @Override
    public Set<Modifier> getModifiers() {
      return myOriginalElement.getModifiers();
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      return ((Name) myElementUtility.getBinaryName(((TypeElement) myOriginalElement.getEnclosingElement()))).toUtf();
    }

    public static JavacElementRefBase fromElement(Element element, Elements elementUtility) {
      if (element instanceof TypeElement) {
        return new JavacElementClassImpl(element, elementUtility);
      }
      else if (element instanceof VariableElement) {
        return new JavacElementFieldImpl(element, elementUtility);
      }
      else if (element instanceof ExecutableElement) {
        return new JavacElementMethodImpl(element, elementUtility);
      }
      throw new AssertionError("unexpected element: " + element + " class: " + element.getClass());
    }
  }

  class JavacElementClassImpl extends JavacElementRefBase implements JavacClass {
   public JavacElementClassImpl(@NotNull Element element, Elements elementUtility) {
      super(element, elementUtility);
    }

    @NotNull
    @Override
    public byte[] getName() {
      return ((Name) myElementUtility.getBinaryName(((TypeElement) myOriginalElement))).toUtf();
    }

    @Override
    public boolean isAnonymous() {
      return ((Name) myOriginalElement.getSimpleName()).isEmpty();
    }
  }

  class JavacElementMethodImpl extends JavacElementRefBase implements JavacMethod {
    public JavacElementMethodImpl(@NotNull Element element, Elements elementUtility) {
      super(element, elementUtility);
    }

    @Override
    public byte getParamCount() {
      return (byte)((ExecutableElement)myOriginalElement).getParameters().size();
    }
  }

  class JavacElementFieldImpl extends JavacElementRefBase implements JavacField {
    public JavacElementFieldImpl(@NotNull Element element, Elements elementUtility) {
      super(element, elementUtility);
    }
  }
}
