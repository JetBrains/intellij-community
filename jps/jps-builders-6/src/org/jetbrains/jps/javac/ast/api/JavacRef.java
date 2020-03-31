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
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor6;
import java.util.Set;

public interface JavacRef {
  JavacRef[] EMPTY_ARRAY = new JavacRef[0];

  @NotNull
  String getName();

  Set<Modifier> getModifiers();

  @NotNull
  String getOwnerName();

  /**
   * @return non-null import descriptor object, if the reference comes from import list
   */
  @Nullable
  ImportProperties getImportProperties();

  abstract class ImportProperties {
    public abstract boolean isStatic();
    public abstract boolean isOnDemand();

    public static ImportProperties create(final boolean isStatic, final boolean isOnDemand) {
      return new ImportProperties() {
        @Override
        public boolean isStatic() {
          return isStatic;
        }

        @Override
        public boolean isOnDemand() {
          return isOnDemand;
        }
      };
    }
  }

  interface JavacClass extends JavacRef {
    boolean isAnonymous();
    boolean isPackageInfo();
  }

  interface JavacMethod extends JavacRef {
    byte getParamCount();
    @Nullable
    String getContainingClass();
  }

  interface JavacField extends JavacRef {
    @Nullable
    String getDescriptor();
    @Nullable
    String getContainingClass();
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

    @Nullable
    @Override
    public ImportProperties getImportProperties() {
      return null;
    }
  }

  class JavacClassImpl extends JavacRefBase implements JavacClass {
    private final boolean myAnonymous;

    public JavacClassImpl(boolean anonymous, Set<Modifier> modifiers, String name) {
      super(name, modifiers);
      myAnonymous = anonymous;
    }

    @NotNull
    @Override
    public String getOwnerName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAnonymous() {
      return myAnonymous;
    }

    @Override
    public boolean isPackageInfo() {
      final String name = getName();
      return name.endsWith(".package-info") || "package-info".equals(name);
    }
  }

  class JavacMethodImpl extends JavacRefBase implements JavacMethod {
    @Nullable
    private final String myContainingClass;
    private final String myOwnerName;
    private final byte myParamCount;

    public JavacMethodImpl(@Nullable String containingClass, String ownerName, byte paramCount, Set<Modifier> modifiers, String name) {
      super(name, modifiers);
      myContainingClass = containingClass != null && !containingClass.isEmpty()? containingClass : null;
      myOwnerName = ownerName;
      myParamCount = paramCount;
    }

    @Override
    @Nullable
    public String getContainingClass() {
      return myContainingClass;
    }

    @Override
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
    @Nullable
    private final String myContainingClass;
    private final String myOwnerName;
    private final String myDescriptor;

    public JavacFieldImpl(@Nullable String containingClass, String ownerName, Set<Modifier> modifiers, String name, String descriptor) {
      super(name, modifiers);
      myContainingClass = containingClass != null && !containingClass.isEmpty()? containingClass : null;
      myOwnerName = ownerName;
      myDescriptor = descriptor != null && !descriptor.isEmpty()? descriptor : null;
    }

    @Override
    @Nullable
    public String getContainingClass() {
      return myContainingClass;
    }

    @Override
    @NotNull
    public String getOwnerName() {
      return myOwnerName;
    }

    @Override
    @Nullable
    public String getDescriptor() {
      return myDescriptor;
    }
  }

  abstract class JavacElementRefBase implements JavacRef {
    protected final @NotNull Element myOriginalElement;
    @Nullable private final Element myQualifier;
    protected final JavacNameTable myNameTableCache;
    private final ImportProperties myImportProps;

    protected JavacElementRefBase(@NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache, ImportProperties importProps) {
      myOriginalElement = element;
      myQualifier = qualifier;
      myNameTableCache = nameTableCache;
      myImportProps = importProps;
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
    @Override
    public ImportProperties getImportProperties() {
      return myImportProps;
    }

    @Nullable
    public static JavacElementRefBase fromElement(@Nullable String containingClass, Element element, Element qualifier, JavacNameTable nameTableCache) {
      return fromElement(containingClass, element, qualifier, nameTableCache, null);
    }
    
    @Nullable
    public static JavacElementRefBase fromElement(@Nullable String containigClass, Element element, Element qualifier, JavacNameTable nameTableCache, @Nullable ImportProperties importProps) {
      if (qualifier != null) {
        TypeMirror type = qualifier.asType();
        if (!isValidType(type)) {
          return null;
        }
      }
      if (element instanceof TypeElement) {
        return new JavacElementClassImpl(element, qualifier, nameTableCache, importProps);
      }
      else if (element instanceof VariableElement) {
        if (qualifier == null && !checkEnclosingElement(element)) return null;
        return new JavacElementFieldImpl(containigClass, element, qualifier, nameTableCache, importProps);
      }
      else if (element instanceof ExecutableElement) {
        if (qualifier == null && !checkEnclosingElement(element)) return null;
        return new JavacElementMethodImpl(containigClass, element, qualifier, nameTableCache, importProps);
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
   public JavacElementClassImpl(@NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache, final ImportProperties importProps) {
      super(element, qualifier, nameTableCache, importProps);
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

    @Override
    public boolean isPackageInfo() {
      return false;
    }
  }

  class JavacElementMethodImpl extends JavacElementRefBase implements JavacMethod {
    @Nullable
    private final String myContainingClass;

    public JavacElementMethodImpl(@Nullable String containingClass, @NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache, final ImportProperties importProps) {
      super(element, qualifier, nameTableCache, importProps);
      myContainingClass = containingClass != null && !containingClass.isEmpty()? containingClass : null;
    }

    @Override
    @Nullable
    public String getContainingClass() {
      return myContainingClass;
    }

    @Override
    public byte getParamCount() {
      return (byte)((ExecutableElement)myOriginalElement).getParameters().size();
    }
  }

  class JavacElementFieldImpl extends JavacElementRefBase implements JavacField {
    @Nullable
    private final String myContainingClass;

    public JavacElementFieldImpl(@Nullable String containingClass, @NotNull Element element, @Nullable Element qualifier, JavacNameTable nameTableCache, final ImportProperties importProps) {
      super(element, qualifier, nameTableCache, importProps);
      myContainingClass = containingClass != null && !containingClass.isEmpty()? containingClass : null;
    }

    @Override
    @Nullable
    public String getContainingClass() {
      return myContainingClass;
    }

    @Override
    @Nullable
    public String getDescriptor() {
      return calcDescriptor(myOriginalElement.asType());
    }

    private String calcDescriptor(TypeMirror type) {
      return new SimpleTypeVisitor6<String, Void>(null) {
        @Override
        public String visitPrimitive(PrimitiveType t, Void aVoid) {
          switch(t.getKind()) {
            case BYTE: return "B";
            case CHAR: return "C";
            case DOUBLE: return "D";
            case FLOAT: return "F";
            case INT: return "I";
            case LONG:return "J";
            case SHORT: return "S";
            case BOOLEAN: return "Z";
          }
          return null;
        }

        @Override
        public String visitArray(ArrayType t, Void aVoid) {
          return "[" + visit(t.getComponentType());
        }

        @Override
        public String visitDeclared(DeclaredType t, Void aVoid) {
          return "L" + myNameTableCache.parseBinaryName(t.asElement()).replace('.', '/') + ";";
        }

        @Override
        public String visitUnknown(TypeMirror t, Void param) {
          return null;
        }
      }.visit(type);
    }
  }
}
