// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac.ast.api;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.ThrowableConsumer;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Modifier;
import java.io.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class JavacFileData {
  public static final String CUSTOM_DATA_PLUGIN_ID = "ast.reference.collector"; // fake plugin name to fit into customOutputData API
  public static final String CUSTOM_DATA_KIND = "JavacFileData";

  private static final byte CLASS_MARKER = 0;
  private static final byte METHOD_MARKER = 1;
  private static final byte FIELD_MARKER = 2;
  private static final byte FUN_EXPR_MARKER = 3;

  private final String myFilePath;
  private final TObjectIntHashMap<JavacRef> myRefs;
  private final List<JavacTypeCast> myCasts;
  private final List<JavacDef> myDefs;
  private final Set<JavacRef> myImplicitRefs;

  public JavacFileData(@NotNull String path,
                       @NotNull TObjectIntHashMap<JavacRef> refs,
                       @NotNull List<JavacTypeCast> casts,
                       @NotNull List<JavacDef> defs,
                       @NotNull Set<JavacRef> implicitRefs) {
    myFilePath = path;
    myRefs = refs;
    myCasts = casts;
    myDefs = defs;
    myImplicitRefs = implicitRefs;
  }

  @NotNull
  public String getFilePath() {
    return myFilePath;
  }

  @NotNull
  public Set<JavacRef> getImplicitToStringRefs() {
    return myImplicitRefs;
  }

  @NotNull
  public TObjectIntHashMap<JavacRef> getRefs() {
    return myRefs;
  }

  @NotNull
  public List<JavacTypeCast> getCasts() {
    return myCasts;
  }

  @NotNull
  public List<JavacDef> getDefs() {
    return myDefs;
  }

  @NotNull
  public byte[] asBytes() {
    final ByteArrayOutputStream os = new ByteArrayOutputStream();
    DataOutputStream stream = new DataOutputStream(os);
    try {
      stream.writeUTF(getFilePath());
      saveRefs(stream, getRefs());
      saveCasts(stream, getCasts());
      saveDefs(stream, getDefs());
      saveImplicitToString(stream, getImplicitToStringRefs());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return os.toByteArray();
  }

  @NotNull
  public static JavacFileData fromBytes(byte[] bytes) {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
    try {
      final String path = in.readUTF();
      final TObjectIntHashMap<JavacRef> refs = readRefs(in);
      final List<JavacTypeCast> casts = readCasts(in);
      final List<JavacDef> defs = readDefs(in);
      final Set<JavacRef> implicitRefs = readImplicitToString(in);
      return new JavacFileData(path, refs, casts, defs, implicitRefs);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void saveRefs(final DataOutput out, TObjectIntHashMap<JavacRef> refs) throws IOException {
    final IOException[] exception = new IOException[]{null};
    DataInputOutputUtilRt.writeINT(out, refs.size());
    if (!refs.forEachEntry(new TObjectIntProcedure<JavacRef>() {
      @Override
      public boolean execute(JavacRef ref, int count) {
        try {
          writeJavacRef(out, ref);
          DataInputOutputUtilRt.writeINT(out, count);
        }
        catch (IOException e) {
          exception[0] = e;
          return false;
        }
        return true;
      }
    })) {
      assert exception[0] != null;
      throw exception[0];
    }
  }

  private static TObjectIntHashMap<JavacRef> readRefs(final DataInput in) throws IOException {
    final int size = DataInputOutputUtilRt.readINT(in);
    TObjectIntHashMap<JavacRef> deserialized = new TObjectIntHashMap<JavacRef>(size);
    for (int i = 0; i < size; i++) {
      deserialized.put(readJavacRef(in), DataInputOutputUtilRt.readINT(in));
    }
    return deserialized;
  }

  private static void saveDefs(final DataOutput out, List<? extends JavacDef> defs) throws IOException {
    DataInputOutputUtilRt.writeSeq(out, defs, new ThrowableConsumer<JavacDef, IOException>() {
      @Override
      public void consume(JavacDef def) throws IOException {
        writeJavacDef(out, def);
      }
    });
  }

  private static List<JavacDef> readDefs(final DataInput in) throws IOException {
    return DataInputOutputUtilRt.readSeq(in, new ThrowableComputable<JavacDef, IOException>() {
      @Override
      public JavacDef compute() throws IOException {
        return readJavacDef(in);
      }
    });
  }

  private static JavacDef readJavacDef(@NotNull DataInput in) throws IOException {
    final byte marker = in.readByte();
    switch (marker) {
      case CLASS_MARKER:
        final int supersSize = in.readInt();
        JavacRef[] superClasses = new JavacRef[supersSize];
        for (int i = 0; i < supersSize; i++) {
          superClasses[i] = readJavacRef(in);
        }
        return new JavacDef.JavacClassDef(readJavacRef(in), superClasses);
      case FUN_EXPR_MARKER:
        return new JavacDef.JavacFunExprDef(readJavacRef(in));
      case METHOD_MARKER:
        JavacRef retType = readJavacRef(in);
        byte dimension = in.readByte();
        boolean isStatic = in.readBoolean();
        return new JavacDef.JavacMemberDef(readJavacRef(in), retType, dimension, isStatic);
      default:
        throw new IllegalStateException("unknown marker " + marker);
    }
  }

  private static void writeJavacDef(@NotNull DataOutput out, JavacDef def) throws IOException {
    if (def instanceof JavacDef.JavacClassDef) {
      out.writeByte(CLASS_MARKER);
      final JavacRef[] superClasses = ((JavacDef.JavacClassDef)def).getSuperClasses();
      out.writeInt(superClasses.length);
      for (JavacRef aClass : superClasses) {
        writeJavacRef(out, aClass);
      }
    }
    else if (def instanceof JavacDef.JavacFunExprDef) {
      out.writeByte(FUN_EXPR_MARKER);
    }
    else if (def instanceof JavacDef.JavacMemberDef) {
      out.writeByte(METHOD_MARKER);
      writeJavacRef(out, ((JavacDef.JavacMemberDef)def).getReturnType());
      out.writeByte(((JavacDef.JavacMemberDef)def).getIteratorKind());
      out.writeBoolean(((JavacDef.JavacMemberDef)def).isStatic());
    }
    else {
      throw new IllegalStateException("unknown type: " + def.getClass());
    }
    writeJavacRef(out, def.getDefinedElement());
  }

  private static void writeJavacRef(@NotNull DataOutput out, JavacRef ref) throws IOException {
    if (ref instanceof JavacRef.JavacClass) {
      out.writeByte(CLASS_MARKER);
      out.writeBoolean(((JavacRef.JavacClass)ref).isAnonymous());
    }
    else if (ref instanceof JavacRef.JavacField) {
      out.writeByte(FIELD_MARKER);
      final String containingClass = ((JavacRef.JavacField)ref).getContainingClass();
      out.writeUTF(containingClass == null? "" : containingClass);
      out.writeUTF(ref.getOwnerName());
      final String descriptor = ((JavacRef.JavacField)ref).getDescriptor();
      out.writeUTF(descriptor == null? "" : descriptor);
    }
    else if (ref instanceof JavacRef.JavacMethod) {
      out.writeByte(METHOD_MARKER);
      final String containingClass = ((JavacRef.JavacMethod)ref).getContainingClass();
      out.writeUTF(containingClass == null? "" : containingClass);
      out.writeUTF(ref.getOwnerName());
      out.write(((JavacRef.JavacMethod)ref).getParamCount());
    }
    else {
      throw new IllegalStateException("unknown type: " + ref.getClass());
    }
    writeModifiers(out, ref.getModifiers());
    out.writeUTF(ref.getName());
  }

  private static JavacRef readJavacRef(@NotNull DataInput in) throws IOException {
    final byte marker = in.readByte();
    switch (marker) {
      case CLASS_MARKER:
        final boolean isAnonymous = in.readBoolean();
        final Set<Modifier> classModifiers = readModifiers(in);
        final String className = in.readUTF();
        return new JavacRef.JavacClassImpl(isAnonymous, classModifiers, className);

      case METHOD_MARKER:
        final String methodContainingClass = in.readUTF();
        final String methodOwnerName = in.readUTF();
        final byte methodParamCount = in.readByte();
        final Set<Modifier> methodModifiers = readModifiers(in);
        final String methodName = in.readUTF();
        return new JavacRef.JavacMethodImpl(methodContainingClass, methodOwnerName, methodParamCount, methodModifiers, methodName);

      case FIELD_MARKER:
        final String fieldContainingClass = in.readUTF();
        final String fieldOwnerName = in.readUTF();
        final String fieldDescriptor = in.readUTF();
        final Set<Modifier> fieldModifiers = readModifiers(in);
        final String fieldName = in.readUTF();
        return new JavacRef.JavacFieldImpl(fieldContainingClass, fieldOwnerName, fieldModifiers, fieldName, fieldDescriptor);

      default:
        throw new IllegalStateException("unknown marker " + marker);
    }
  }

  private static void writeModifiers(final DataOutput output, Set<Modifier> modifiers) throws IOException {
    DataInputOutputUtilRt.writeSeq(output, modifiers, new ThrowableConsumer<Modifier, IOException>() {
      @Override
      public void consume(Modifier modifier) throws IOException {
        output.writeUTF(modifier.name());
      }
    });
  }

  private static Set<Modifier> readModifiers(final DataInput input) throws IOException {
    final List<Modifier> modifierList = DataInputOutputUtilRt.readSeq(input, new ThrowableComputable<Modifier, IOException>() {
      @Override
      public Modifier compute() throws IOException {
        return Modifier.valueOf(input.readUTF());
      }
    });
    return modifierList.isEmpty() ? Collections.<Modifier>emptySet() : EnumSet.copyOf(modifierList);
  }

  private static void saveCasts(@NotNull final DataOutput output, @NotNull List<? extends JavacTypeCast> casts) throws IOException {
    DataInputOutputUtilRt.writeSeq(output, casts, new ThrowableConsumer<JavacTypeCast, IOException>() {
      @Override
      public void consume(JavacTypeCast cast) throws IOException {
        writeJavacRef(output, cast.getOperandType());
        writeJavacRef(output, cast.getCastType());
      }
    });
  }

  @NotNull
  private static List<JavacTypeCast> readCasts(@NotNull final DataInput input) throws IOException {
    return DataInputOutputUtilRt.readSeq(input, new ThrowableComputable<JavacTypeCast, IOException>() {
      @Override
      public JavacTypeCast compute() throws IOException {
        return new JavacTypeCast((JavacRef.JavacClass)readJavacRef(input), (JavacRef.JavacClass)readJavacRef(input));
      }
    });
  }

  @NotNull
  private static Set<JavacRef> readImplicitToString(@NotNull DataInputStream in) throws IOException {
    int size = DataInputOutputUtilRt.readINT(in);
    THashSet<JavacRef> result = new THashSet<JavacRef>(size);
    for (int i = 0; i < size; i++) {
      result.add(readJavacRef(in));
    }
    return result;
  }

  private static void saveImplicitToString(@NotNull DataOutputStream out, @NotNull Set<? extends JavacRef> refs) throws IOException {
    DataInputOutputUtilRt.writeINT(out, refs.size());
    for (JavacRef ref : refs) {
      writeJavacRef(out, ref);
    }
  }
}
