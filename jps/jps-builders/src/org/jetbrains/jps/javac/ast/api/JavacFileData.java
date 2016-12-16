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

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Modifier;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class JavacFileData {
  private final String myFilePath;
  private final Collection<JavacRef> myRefs;
  private final Collection<JavacRef> myImportRefs;
  private final List<JavacDef> myDefs;

  public JavacFileData(@NotNull String path,
                       @NotNull Collection<JavacRef> refs,
                       @NotNull Collection<JavacRef> importRefs,
                       @NotNull List<JavacDef> defs) {
    myFilePath = path;
    myRefs = refs;
    myImportRefs = importRefs;
    myDefs = defs;
  }

  @NotNull
  public String getFilePath() {
    return myFilePath;
  }

  @NotNull
  public Collection<JavacRef> getRefs() {
    return myRefs;
  }

  @NotNull
  public Collection<JavacRef> getImportRefs() {
    return myImportRefs;
  }

  @NotNull
  public List<JavacDef> getDefs() {
    return myDefs;
  }

  @NotNull
  public byte[] asBytes() {
    final BufferExposingByteArrayOutputStream os = new BufferExposingByteArrayOutputStream();
    DataOutputStream stream = new DataOutputStream(os);
    try {
      EXTERNALIZER.save(stream, this);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return os.toByteArray();
  }

  @NotNull
  public static JavacFileData fromBytes(byte[] bytes) {
    final UnsyncByteArrayInputStream is = new UnsyncByteArrayInputStream(bytes);
    try {
      return EXTERNALIZER.read(new DataInputStream(is));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final DataExternalizer<JavacFileData> EXTERNALIZER = new DataExternalizer<JavacFileData>() {
    DataExternalizer<JavacRef> myRefSeqExternalizer = createRefExternalizer();
    DataExternalizer<JavacDef> myDefSeqExternalizer = createDefExternalizer();

    @Override
    public void save(@NotNull DataOutput out, JavacFileData data) throws IOException {
      EnumeratorStringDescriptor.INSTANCE.save(out, data.getFilePath());
      saveRefs(out, data.getRefs());
      saveRefs(out, data.getImportRefs());
      saveDefs(out, data.getDefs());
    }

    @Override
    public JavacFileData read(@NotNull DataInput in) throws IOException {
      return new JavacFileData(EnumeratorStringDescriptor.INSTANCE.read(in),
                               readRefs(in),
                               readRefs(in),
                               readDefs(in));
    }


    private void saveRefs(final DataOutput out, Collection<JavacRef> refs) throws IOException {
      DataInputOutputUtil.writeSeq(out, refs, new ThrowableConsumer<JavacRef, IOException>() {
        @Override
        public void consume(JavacRef ref) throws IOException {
          myRefSeqExternalizer.save(out, ref);
        }
      });
    }

    private Collection<JavacRef> readRefs(final DataInput in) throws IOException {
      return DataInputOutputUtil.readSeq(in, new ThrowableComputable<JavacRef, IOException>() {
        @Override
        public JavacRef compute() throws IOException {
          return myRefSeqExternalizer.read(in);
        }
      });
    }

    private void saveDefs(final DataOutput out, List<JavacDef> defs) throws IOException {
      DataInputOutputUtil.writeSeq(out, defs, new ThrowableConsumer<JavacDef, IOException>() {
        @Override
        public void consume(JavacDef def) throws IOException {
          myDefSeqExternalizer.save(out, def);
        }
      });
    }

    private List<JavacDef> readDefs(final DataInput in) throws IOException {
      return DataInputOutputUtil.readSeq(in, new ThrowableComputable<JavacDef, IOException>() {
        @Override
        public JavacDef compute() throws IOException {
          return myDefSeqExternalizer.read(in);
        }
      });
    }

  };

  private static DataExternalizer<JavacDef> createDefExternalizer() {
    return new DataExternalizer<JavacDef>() {
      private static final byte CLASS_MARKER = 0;
      private static final byte FUN_EXPR_MARKER = 1;

      DataExternalizer<JavacRef> refExternalizer = createRefExternalizer();

      @Override
      public void save(@NotNull DataOutput out, JavacDef def) throws IOException {
        if (def instanceof JavacDef.JavacClassDef) {
          out.writeByte(CLASS_MARKER);
          final JavacRef[] superClasses = ((JavacDef.JavacClassDef)def).getSuperClasses();
          out.writeInt(superClasses.length);
          for (JavacRef aClass : superClasses) {
            refExternalizer.save(out, aClass);
          }
        }
        else if (def instanceof JavacDef.JavacFunExprDef) {
          out.writeByte(FUN_EXPR_MARKER);
        } else {
          throw new IllegalStateException("unknown type: " + def.getClass());
        }
        refExternalizer.save(out, def.getDefinedElement());
      }

      @Override
      public JavacDef read(@NotNull DataInput in) throws IOException {
        final byte marker = in.readByte();
        switch (marker) {
          case CLASS_MARKER:
            final int supersSize = in.readInt();
            JavacRef[] superClasses = new JavacRef[supersSize];
            for (int i = 0; i < supersSize; i++) {
              superClasses[i] = refExternalizer.read(in);
            }
            return new JavacDef.JavacClassDef(refExternalizer.read(in), superClasses);
          case FUN_EXPR_MARKER:
            return new JavacDef.JavacFunExprDef(refExternalizer.read(in));
          default: throw new IllegalStateException("unknown marker " + marker);
        }
      }
    };
  }

  private static DataExternalizer<JavacRef> createRefExternalizer() {
    return new DataExternalizer<JavacRef>() {
      private static final byte CLASS_MARKER = 0;
      private static final byte METHOD_MARKER = 1;
      private static final byte FIELD_MARKER = 2;

      @Override
      public void save(@NotNull DataOutput out, JavacRef ref) throws IOException {
        if (ref instanceof JavacRef.JavacClass) {
          out.writeByte(CLASS_MARKER);
          out.writeBoolean(((JavacRef.JavacClass)ref).isAnonymous());
        }
        else if (ref instanceof JavacRef.JavacField) {
          out.writeByte(FIELD_MARKER);
          writeBytes(out, ref.getOwnerName());
        }
        else if (ref instanceof JavacRef.JavacMethod) {
          out.writeByte(METHOD_MARKER);
          writeBytes(out, ref.getOwnerName());
          out.write(((JavacRef.JavacMethod)ref).getParamCount());
        } else {
          throw new IllegalStateException("unknown type: " + ref.getClass());
        }
        writeModifiers(out, ref.getModifiers());
        writeBytes(out, ref.getName());
      }

      @Override
      public JavacRef read(@NotNull DataInput in) throws IOException {
        final byte marker = in.readByte();
        switch (marker) {
          case CLASS_MARKER:
            return new JavacRef.JavacClassImpl(in.readBoolean(), readModifiers(in), readBytes(in));
          case METHOD_MARKER:
            return new JavacRef.JavacMethodImpl(readBytes(in), in.readByte(), readModifiers(in), readBytes(in));
          case FIELD_MARKER:
            return new JavacRef.JavacFieldImpl(readBytes(in), readModifiers(in), readBytes(in));
          default:
            throw new IllegalStateException("unknown marker " + marker);
        }
      }

      private void writeBytes(DataOutput out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
      }

      private byte[] readBytes(DataInput in) throws IOException {
        final int len = in.readInt();
        final byte[] buf = new byte[len];
        in.readFully(buf);
        return buf;
      }

      private void writeModifiers(final DataOutput output, Set<Modifier> modifiers) throws IOException {
        DataInputOutputUtil.writeSeq(output, modifiers, new ThrowableConsumer<Modifier, IOException>() {
          @Override
          public void consume(Modifier modifier) throws IOException {
            IOUtil.writeUTF(output, modifier.name());
          }
        });
      }

      private Set<Modifier> readModifiers(final DataInput input) throws IOException {
        return EnumSet.copyOf(DataInputOutputUtil.readSeq(input, new ThrowableComputable<Modifier, IOException>() {
          @Override
          public Modifier compute() throws IOException {
            return null;
          }
        }));
      }
    };
  }
}
