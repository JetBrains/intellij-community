// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.DataReader;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.Externalizer;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class JvmNodeElementExternalizer {
  
  /** @noinspection SSBasedInspection*/
  private enum ElementDescriptor {
    CLASS(JvmClass.class, JvmClass::new),
    MODULE(JvmModule.class, JvmModule::new),
    CLASS_USAGE(ClassUsage.class, ClassUsage::new),
    CLASS_AS_GENERIC_BOUND_USAGE(ClassAsGenericBoundUsage.class, ClassAsGenericBoundUsage::new),
    CLASS_EXTENDS_USAGE(ClassExtendsUsage.class, ClassExtendsUsage::new),
    CLASS_NEW_USAGE(ClassNewUsage.class, ClassNewUsage::new),
    METHOD_USAGE(MethodUsage.class, MethodUsage::new),
    FILED_USAGE(FieldUsage.class, FieldUsage::new),
    FIELD_ASSIGN_USAGE(FieldAssignUsage.class, FieldAssignUsage::new),
    ANNOTATION_USAGE(AnnotationUsage.class, AnnotationUsage::new),
    IMPORT_STATIC_MEMBER_USAGE(ImportStaticMemberUsage.class, ImportStaticMemberUsage::new),
    IMPORT_STATIC_ON_DEMAND_USAGE(ImportStaticOnDemandUsage.class, ImportStaticOnDemandUsage::new),
    MODULE_USAGE(ModuleUsage.class, ModuleUsage::new);

    public final Class<? extends ExternalizableGraphElement> elementClass;
    public final DataReader<? extends ExternalizableGraphElement> factory;

    ElementDescriptor(Class<? extends ExternalizableGraphElement> elementClass, DataReader<? extends ExternalizableGraphElement> factory) {
      this.elementClass = elementClass;
      this.factory = factory;
    }

    private static final Map<Class<? extends ExternalizableGraphElement>, ElementDescriptor> ourClassToElementMap = new HashMap<>();
    private static final Map<Integer, ElementDescriptor> ourOrdinalToElementMap = new HashMap<>();
    static {
      for (ElementDescriptor elem : values()) {
        ourClassToElementMap.put(elem.elementClass, elem);
        ourOrdinalToElementMap.put(elem.ordinal(), elem);
      }
    }

    public static ElementDescriptor find(int ordinal) {
      return ourOrdinalToElementMap.get(ordinal);
    }
    public static ElementDescriptor find(ExternalizableGraphElement element) {
      return find(element.getClass());
    }
    public static ElementDescriptor find(Class<? extends ExternalizableGraphElement> ordinal) {
      return ourClassToElementMap.get(ordinal);
    }
  }
  
  private static final Externalizer<? extends ExternalizableGraphElement> ourMultitypeExternalizer = new Externalizer<>() {
    @Override
    public ExternalizableGraphElement load(DataInput in) throws IOException {
      return ElementDescriptor.find(RW.readINT(in)).factory.load(in);
    }

    @Override
    public void save(DataOutput out, ExternalizableGraphElement value) throws IOException {
      RW.writeINT(out, ElementDescriptor.find(value).ordinal());
      value.write(out);
    }
  };

  public static <T extends ExternalizableGraphElement> Externalizer<T> getMultitypeExternalizer() {
    //noinspection unchecked
    return (Externalizer<T>)ourMultitypeExternalizer;
  }

  public static <T extends ExternalizableGraphElement> Externalizer<T> getMonotypeExternalizer(DataReader<? extends T> reader) {
    return new Externalizer<>() {
      @Override
      public T load(DataInput in) throws IOException {
        return reader.load(in);
      }

      @Override
      public void save(DataOutput out, T value) throws IOException {
        value.write(out);
      }
    };
  }

}
