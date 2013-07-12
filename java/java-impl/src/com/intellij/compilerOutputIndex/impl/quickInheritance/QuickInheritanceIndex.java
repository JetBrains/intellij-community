package com.intellij.compilerOutputIndex.impl.quickInheritance;

import com.intellij.compilerOutputIndex.api.fs.AsmUtil;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.compilerOutputIndex.api.descriptor.HashSetKeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.asm4.Type;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class QuickInheritanceIndex extends CompilerOutputBaseIndex<String, Set<String>> {

  public static QuickInheritanceIndex getInstance(final Project project) {
    return CompilerOutputIndexer.getInstance(project).getIndex(QuickInheritanceIndex.class);
  }

  public QuickInheritanceIndex() {
    super(new EnumeratorStringDescriptor(), new HashSetKeyDescriptor<String>(new EnumeratorStringDescriptor()));
  }

  @Override
  protected ID<String, Set<String>> getIndexId() {
    return generateIndexId(QuickInheritanceIndex.class);
  }

  protected Set<String> getSupers(final String classQName) {
    try {
      final ValueContainer<Set<String>> valueContainer = myIndex.getData(classQName);
      final Ref<Set<String>> setRef = Ref.create();
      valueContainer.forEach(new ValueContainer.ContainerAction<Set<String>>() {
        @Override
        public boolean perform(final int id, final Set<String> value) {
          setRef.set(value);
          return false;
        }
      });
      final Set<String> supers = setRef.get();
      if (supers == null) {
        return Collections.emptySet();
      }
      return supers;
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected int getVersion() {
    return 0;
  }

  @Override
  protected DataIndexer<String, Set<String>, ClassReader> getIndexer() {
    return new DataIndexer<String, Set<String>, ClassReader>() {
      @NotNull
      @Override
      public Map<String, Set<String>> map(final ClassReader inputData) {
        final Map<String, Set<String>> map = new HashMap<String, Set<String>>();
        inputData.accept(new ClassVisitor(Opcodes.ASM4) {
          @Override
          public void visit(final int version,
                            final int access,
                            final String name,
                            final String signature,
                            final String superName,
                            final String[] interfaces) {
            final String className = Type.getObjectType(name).getClassName();
            if (className != null) {
              final HashSet<String> value = ContainerUtil.newHashSet(AsmUtil.getQualifiedClassNames(interfaces, superName));
              value.remove(CommonClassNames.JAVA_LANG_OBJECT);
              map.put(className, value);
            }
          }
        }, Opcodes.ASM4);
        return map;
      }
    };
  }
}
