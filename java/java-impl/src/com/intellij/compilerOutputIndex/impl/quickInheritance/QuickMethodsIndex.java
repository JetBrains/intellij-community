package com.intellij.compilerOutputIndex.impl.quickInheritance;

import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.compilerOutputIndex.api.descriptor.HashSetKeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.MethodVisitor;
import org.jetbrains.asm4.Opcodes;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class QuickMethodsIndex extends CompilerOutputBaseIndex<String, Set<String>> {

  public static QuickMethodsIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(QuickMethodsIndex.class);
  }

  public QuickMethodsIndex() {
    super(new EnumeratorStringDescriptor(), new HashSetKeyDescriptor<String>(new EnumeratorStringDescriptor()));
  }

  @Override
  protected ID<String, Set<String>> getIndexId() {
    return generateIndexId(QuickMethodsIndex.class);
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  protected Set<String> getMethodsNames(final String classQName) {
    final Ref<Set<String>> methodsRef = Ref.create();
    try {
      myIndex.getData(classQName).forEach(new ValueContainer.ContainerAction<Set<String>>() {
        @Override
        public boolean perform(final int id, final Set<String> value) {
          methodsRef.set(value);
          return true;
        }
      });
      final Set<String> methods = methodsRef.get();
      return methods == null ? Collections.<String>emptySet() : methods;
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected DataIndexer<String, Set<String>, ClassReader> getIndexer() {
    return new DataIndexer<String, Set<String>, ClassReader>() {
      @NotNull
      @Override
      public Map<String, Set<String>> map(final ClassReader inputData) {
        final Map<String, Set<String>> map = new HashMap<String, Set<String>>();
        inputData.accept(new ClassVisitor(Opcodes.ASM4) {

          private String myClassName;
          private final HashSet<String> myMethodNames = new HashSet<String>();

          @Override
          public void visit(final int i, final int i2, final String name, final String s2, final String s3, final String[] strings) {
            myClassName = AsmUtil.getQualifiedClassName(name);
          }

          @Override
          public void visitEnd() {
            map.put(myClassName, myMethodNames);
          }

          @Nullable
          @Override
          public MethodVisitor visitMethod(final int access,
                                           final String name,
                                           final String desc,
                                           final String sign,
                                           final String[] exceptions) {
            if ((access & Opcodes.ACC_STATIC) == 0) {
              myMethodNames.add(name);
            }
            return null;
          }
        }, Opcodes.ASM4);
        return map;
      }
    };
  }
}
