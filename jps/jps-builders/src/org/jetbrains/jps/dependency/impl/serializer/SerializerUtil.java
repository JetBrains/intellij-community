// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl.serializer;

import com.intellij.serialization.SerializationException;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.StringReferenceID;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SerializerUtil {
  static void writeProto(Proto proto, DataOutput out) throws IOException {
    DataInputOutputUtil.writeINT(out, proto.getFlags().hashCode());
    out.writeUTF(proto.getSignature());
    out.writeUTF(proto.getName());
    // Write annotations
    int annotationCount = 0;
    for (TypeRepr.ClassType type : proto.getAnnotations()) {
      annotationCount++;
    }
    DataInputOutputUtil.writeINT(out, annotationCount);
    for (TypeRepr.ClassType annotation : proto.getAnnotations()) {
      out.writeUTF(annotation.getJvmName());
    }
  }

  static void writeJVMClassNode(JVMClassNode jvmClassNode, DataOutput out) throws IOException {
    writeProto(jvmClassNode, out);

    out.writeUTF(jvmClassNode.getOutFilePath());
    // Write Usages
    int usagesCount = 0;
    for (Object usage : jvmClassNode.getUsages()) {
      usagesCount++;
    }
    DataInputOutputUtil.writeINT(out, usagesCount);
    for (Object usage : jvmClassNode.getUsages()) {
      if (usage instanceof Usage) {
        writeUsage((Usage)usage, out);
      }
      else {
        throw new SerializationException("Serialization error: Unexpected type for 'usage'");
      }
    }
  }

  static void writeJvmClass(JvmClass jvmClass, DataOutput out) throws IOException {
    writeJVMClassNode(jvmClass, out);
    out.writeUTF(jvmClass.getSuperFqName());
    out.writeUTF(jvmClass.getOuterFqName());
    //  Write myInterfaces;
    int interfacesCount = 0;
    for (String myInterface : jvmClass.getInterfaces()) {
      interfacesCount++;
    }
    DataInputOutputUtil.writeINT(out, interfacesCount);
    for (String myInterface : jvmClass.getInterfaces()) {
      out.writeUTF(myInterface);
    }
    //  Write myFields
    int fieldsCount = 0;
    for (JvmField field : jvmClass.getFields()) fieldsCount++;
    DataInputOutputUtil.writeINT(out, fieldsCount);
    for (JvmField field : jvmClass.getFields()) {
      writeJvmField(field, out);
    }

    //  Write myMethods
    int methodCount = 0;
    for (JvmMethod jvmMethod : jvmClass.getMethods()) methodCount++;
    DataInputOutputUtil.writeINT(out, methodCount);
    for (JvmMethod jvmMethod : jvmClass.getMethods()) {
      writeJvmMethod(jvmMethod, out);
    }

    //  Write AnnotationTargets
    int elemTypeCount = 0;
    for (ElemType elemType : jvmClass.getAnnotationTargets()) elemTypeCount++;
    DataInputOutputUtil.writeINT(out, elemTypeCount);
    for (ElemType elemType : jvmClass.getAnnotationTargets()) {
      writeElemType(elemType, out);
    }

    if (jvmClass.getRetentionPolicy() != null) {
      out.writeUTF(jvmClass.getRetentionPolicy().name());
    }
    else {
      out.writeUTF("");
    }
  }

  static void writeJvmField(JvmField jvmField, DataOutput out) throws IOException {
    writeProtoMember(jvmField, out);
  }

  static void writeProtoMember(ProtoMember protoMember, DataOutput out) throws IOException {
    writeProto(protoMember, out);
    writeTypeRepr(protoMember.getType(), out);
    writeValueObject(protoMember.getValue(), out);
  }

  static void writeTypeRepr(TypeRepr typeRepr, DataOutput out) throws IOException {
    String typeReprName = typeRepr.getClass().getSimpleName();
    out.writeUTF(typeReprName);
    switch (typeReprName) {
      case "PrimitiveType":
        out.writeUTF(typeRepr.getDescriptor());
        break;
      case "ClassType":
        out.writeUTF(((TypeRepr.ClassType)typeRepr).getJvmName());
        break;
      case "ArrayType":
        //TODO: check if correct, be careful on reading
        out.writeUTF(typeRepr.getDescriptor());
        break;
    }
  }

  static void writeValueObject(Object obj, DataOutput out) throws IOException {
    try {
      final Class valueType = obj != null ? obj.getClass() : null;
      if (valueType != null && valueType.isArray()) {
        final int length = Array.getLength(obj);
        final Class dataType = length > 0 ? Array.get(obj, 0).getClass() : valueType.getComponentType();
        final DataDescriptor descriptor = DataDescriptor.findByValueType(dataType);
        out.writeByte(-descriptor.getId());
        if (descriptor != DataDescriptor.NONE) {
          DataInputOutputUtil.writeINT(out, length);
          for (int idx = 0; idx < length; idx++) {
            final Object element = Array.get(obj, idx);
            //noinspection unchecked
            descriptor.save(out, element);
          }
        }
      }
      else {
        final DataDescriptor descriptor = DataDescriptor.findByValueType(valueType);
        out.writeByte(descriptor.getId());
        //noinspection unchecked
        descriptor.save(out, obj);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // copy-pasted from org.jetbrains.jps.builders.java.dependencyView.ProtoMember.DataDescriptor
  private static abstract class DataDescriptor<T> {
    public static final DataDescriptor
      NONE = new DataDescriptor(0, null) {
      @Override
      public Object load(DataInput out) {
        return null;
      }

      @Override
      public void save(DataOutput out, Object value) throws IOException {
      }
    };
    public static final DataDescriptor<String>
      STRING = new DataDescriptor<String>(1, String.class) {
      @Override
      public String load(DataInput in) throws IOException {
        return in.readUTF();
      }

      @Override
      public void save(DataOutput out, String value) throws IOException {
        out.writeUTF(value);
      }
    };
    public static final DataDescriptor<Integer>
      INTEGER = new DataDescriptor<Integer>(2, Integer.class) {
      @Override
      public Integer load(DataInput in) throws IOException {
        return DataInputOutputUtil.readINT(in);
      }

      @Override
      public void save(DataOutput out, Integer value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.intValue());
      }
    };
    public static final DataDescriptor<Long>
      LONG = new DataDescriptor<Long>(3, Long.class) {
      @Override
      public Long load(DataInput in) throws IOException {
        return in.readLong();
      }

      @Override
      public void save(DataOutput out, Long value) throws IOException {
        out.writeLong(value.longValue());
      }
    };
    public static final DataDescriptor<Float>
      FLOAT = new DataDescriptor<Float>(4, Float.class) {
      @Override
      public Float load(DataInput in) throws IOException {
        return in.readFloat();
      }

      @Override
      public void save(DataOutput out, Float value) throws IOException {
        out.writeFloat(value.floatValue());
      }
    };
    public static final DataDescriptor<Double>
      DOUBLE = new DataDescriptor<Double>(5, Double.class) {
      @Override
      public Double load(DataInput in) throws IOException {
        return in.readDouble();
      }

      @Override
      public void save(DataOutput out, Double value) throws IOException {
        out.writeDouble(value.doubleValue());
      }
    };
    public static final DataDescriptor<Type>
      TYPE = new DataDescriptor<Type>(6, Type.class) {
      @Override
      public Type load(DataInput in) throws IOException {
        return Type.getType(in.readUTF());
      }

      @Override
      public void save(DataOutput out, Type value) throws IOException {
        out.writeUTF(value.getDescriptor());
      }
    };

    private final byte myId;
    @Nullable
    private final Class<T> myDataType;

    private DataDescriptor(int id, Class<T> dataType) {
      myId = (byte)id;
      myDataType = dataType;
    }

    public byte getId() {
      return myId;
    }

    @Nullable
    public Class<T> getDataType() {
      return myDataType;
    }

    public abstract void save(DataOutput out, T value) throws IOException;

    public abstract T load(DataInput in) throws IOException;

    @NotNull
    public static DataDescriptor findById(byte tag) {
      if (STRING.getId() == tag) {
        return STRING;
      }
      if (INTEGER.getId() == tag) {
        return INTEGER;
      }
      if (LONG.getId() == tag) {
        return LONG;
      }
      if (FLOAT.getId() == tag) {
        return FLOAT;
      }
      if (DOUBLE.getId() == tag) {
        return DOUBLE;
      }
      if (TYPE.getId() == tag) {
        return TYPE;
      }
      if (NONE.getId() == tag) {
        return NONE;
      }
      assert false : "Unknown descriptor tag: " + tag;
      return NONE;
    }

    public static DataDescriptor findByValueType(@Nullable Class<?> dataType) {
      if (dataType != null) {
        if (dataType.equals(STRING.getDataType())) {
          return STRING;
        }
        if (dataType.equals(INTEGER.getDataType())) {
          return INTEGER;
        }
        if (dataType.equals(LONG.getDataType())) {
          return LONG;
        }
        if (dataType.equals(FLOAT.getDataType())) {
          return FLOAT;
        }
        if (dataType.equals(DOUBLE.getDataType())) {
          return DOUBLE;
        }
        //noinspection ConstantConditions
        if (TYPE.getDataType().isAssignableFrom(dataType)) {
          return TYPE;
        }
      }
      return NONE;
    }
  }


  static void writeElemType(ElemType elemType, DataOutput out) throws IOException {
    out.writeUTF(elemType.name());
  }

  static void writeJvmMethod(JvmMethod jvmMethod, DataOutput out) throws IOException {
    writeProtoMember(jvmMethod, out);

    // Write ArgTypes
    int argTypesCount = 0;
    for (TypeRepr typeRepr : jvmMethod.getArgTypes()) argTypesCount++;
    DataInputOutputUtil.writeINT(out, argTypesCount);
    for (TypeRepr typeRepr : jvmMethod.getArgTypes()) {
      writeTypeRepr(typeRepr, out);
    }

    // Write ParamAnnotations
    DataInputOutputUtil.writeINT(out, jvmMethod.getParamAnnotations().size());
    for (ParamAnnotation paramAnnotation : jvmMethod.getParamAnnotations()) {
      DataInputOutputUtil.writeINT(out, paramAnnotation.paramIndex);
      out.writeUTF(paramAnnotation.type.getJvmName());
    }

    // Write myExceptions
    DataInputOutputUtil.writeINT(out, jvmMethod.getExceptions().size());
    for (TypeRepr.ClassType exceptions : jvmMethod.getExceptions()) {
      out.writeUTF(exceptions.getJvmName());
    }
    out.writeUTF(jvmMethod.getDescriptor());
  }

  // Usages
  static void writeUsage(Usage usage, DataOutput out) throws IOException {
    String usageName = usage.getClass().getSimpleName();
    out.writeUTF(usageName);
    switch (usageName) {
      case "FieldAssignUsage":
        writeFieldAssignUsage((FieldAssignUsage)usage, out);
        break;
      case "FieldUsage":
        writeFieldUsage((FieldUsage)usage, out);
        break;
      case "MethodUsage":
        writeMethodUsage((MethodUsage)usage, out);
        break;
      case "ImportStaticMemberUsage":
        writeImportStaticMemberUsage((ImportStaticMemberUsage)usage, out);
        break;
      case "ClassAsGenericBoundUsage":
      case "ClassExtendsUsage":
      case "ClassNewUsage":
      case "ClassUsage":
        writeClassUsage((ClassUsage)usage, out);
        break;
      case "AnnotationUsage":
        writeAnnotationUsage((AnnotationUsage)usage, out);
        break;
      case "ModuleUsage":
        writeModuleUsage((ModuleUsage)usage, out);
        break;
      case "ImportStaticOnDemandUsage":
        writeImportStaticOnDemandUsage((ImportStaticOnDemandUsage)usage, out);
        break;
      default:
        throw new SerializationException("Serialization error: Unexpected type for 'usage':" + usageName);
    }
  }

  static void writeFieldAssignUsage(FieldAssignUsage fieldAssignUsage, DataOutput out) throws IOException {
    writeFieldUsage(fieldAssignUsage, out);
  }

  static void writeFieldUsage(FieldUsage fieldUsage, DataOutput out) throws IOException {
    ReferenceID refId = fieldUsage.getElementOwner();
    if (refId instanceof StringReferenceID) {
      out.writeUTF(((StringReferenceID)refId).getValue());
    }
    else {
      throw new SerializationException("Serialization error: Unexpected ReferenceID type: " + refId.getClass().getTypeName());
    }
    out.writeUTF(fieldUsage.getName());

    out.writeUTF(fieldUsage.getDescriptor());
  }

  public static void writeMethodUsage(MethodUsage methodUsage, DataOutput out) throws IOException {
    ReferenceID refId = methodUsage.getElementOwner();
    if (refId instanceof StringReferenceID) {
      out.writeUTF(((StringReferenceID)refId).getValue());
    }
    else {
      throw new SerializationException("Serialization error: Unexpected ReferenceID type: " + refId.getClass().getTypeName());
    }
    out.writeUTF(methodUsage.getName());

    out.writeUTF(methodUsage.getDescriptor());
  }

  public static void writeImportStaticMemberUsage(ImportStaticMemberUsage importStaticMemberUsage, DataOutput out) throws IOException {
    ReferenceID refId = importStaticMemberUsage.getElementOwner();
    if (refId instanceof StringReferenceID) {
      out.writeUTF(((StringReferenceID)refId).getValue());
    }
    else {
      throw new SerializationException("Serialization error: Unexpected ReferenceID type: " + refId.getClass().getTypeName());
    }
    out.writeUTF(importStaticMemberUsage.getName());
  }

  public static void writeClassUsage(ClassUsage classUsage, DataOutput out) throws IOException {
    out.writeUTF(classUsage.getClassName());
  }

  public static void writeModuleUsage(ModuleUsage moduleUsage, DataOutput out) throws IOException {
    out.writeUTF(moduleUsage.getModuleName());
  }

  public static void writeImportStaticOnDemandUsage(ImportStaticOnDemandUsage importStaticOnDemandUsage, DataOutput out)
    throws IOException {
    out.writeUTF(importStaticOnDemandUsage.getImportedClassName());
  }

  public static void writeAnnotationUsage(AnnotationUsage annotationUsage, DataOutput out) throws IOException {
    out.writeUTF(annotationUsage.getClassType().getJvmName());
    // UserArgNames
    int userArgNamesCount = 0;
    for (String userArgName : annotationUsage.getUserArgNames()) {
      userArgNamesCount++;
    }
    DataInputOutputUtil.writeINT(out, userArgNamesCount);
    for (String userArgName : annotationUsage.getUserArgNames()) {
      out.writeUTF(userArgName);
    }

    //  Write Targets
    int targetsCount = 0;
    for (ElemType elemType : annotationUsage.getTargets()) targetsCount++;
    DataInputOutputUtil.writeINT(out, targetsCount);
    for (ElemType elemType : annotationUsage.getTargets()) {
      writeElemType(elemType, out);
    }
  }


  static Proto readProto(DataInput in) throws IOException {
    int flags = DataInputOutputUtil.readINT(in);
    String signature = in.readUTF();
    String name = in.readUTF();
    // Read annotations
    int annotationCount = DataInputOutputUtil.readINT(in);
    List<TypeRepr.ClassType> annotations = new ArrayList<>(annotationCount);
    for (int i = 0; i < annotationCount; i++) {
      String annotationJvmName = in.readUTF();
      annotations.add(new TypeRepr.ClassType(annotationJvmName));
    }
    return new Proto(new JVMFlags(flags), signature, name, annotations);
  }

  static JVMClassNode readJvmClassNode(DataInput in) throws IOException {
    Proto proto = readProto(in);
    //StringReferenceID refId = new StringReferenceID(in.readUTF());
    String outFilePath = in.readUTF();
    int usagesCount = DataInputOutputUtil.readINT(in);
    List<Usage> usages = new ArrayList<>(usagesCount);
    for (int i = 0; i < usagesCount; i++) {
      Usage usage = readUsage(in);
      usages.add(usage);
    }
    return new JVMClassNode(proto.getFlags(), proto.getSignature(), proto.getName(), outFilePath, proto.getAnnotations(), usages) {
      @Override
      public Difference difference(DiffCapable past) {
        return null;
      }
    };
  }

  static FieldAssignUsage readFieldAssignUsage(DataInput in) throws IOException {
    FieldUsage fieldUsage = readFieldUsage(in);
    return (FieldAssignUsage)fieldUsage;
  }

  static FieldUsage readFieldUsage(DataInput in) throws IOException {
    String refId = in.readUTF();
    String name = in.readUTF();
    String descriptor = in.readUTF();
    return new FieldUsage(refId, name, descriptor);
  }

  static JvmClass readJvmClass(DataInput in) throws IOException {
    JVMClassNode jvmClassNode = readJvmClassNode(in);

    String superFqName = in.readUTF();
    String outerFqName = in.readUTF();
    //  Read myInterfaces;
    int interfacesCount = DataInputOutputUtil.readINT(in);
    List<String> interfaces = new ArrayList<>(interfacesCount);
    for (int i = 0; i < interfacesCount; i++) {
      String myInterface = in.readUTF();
      interfaces.add(myInterface);
    }
    //  Read myFields
    int fieldsCount = DataInputOutputUtil.readINT(in);
    List<JvmField> fields = new ArrayList<>(fieldsCount);
    for (int i = 0; i < fieldsCount; i++) {
      JvmField field = readJvmField(in);
      fields.add(field);
    }

    //  Read myMethods
    int methodCount = DataInputOutputUtil.readINT(in);
    List<JvmMethod> methods = new ArrayList<>(methodCount);
    for (int i = 0; i < methodCount; i++) {
      JvmMethod method = readJvmMethod(in);
      methods.add(method);
    }

    //  Read AnnotationTargets
    int elemTypeCount = DataInputOutputUtil.readINT(in);
    List<ElemType> annotationTargets = new ArrayList<>(elemTypeCount);
    for (int i = 0; i < elemTypeCount; i++) {
      ElemType elemType = readElemType(in);
      annotationTargets.add(elemType);
    }

    RetentionPolicy retentionPolicy;
    String retentionPolicyName = in.readUTF();
    if (retentionPolicyName.isEmpty()) {
      retentionPolicy = null;
    }
    else {
      retentionPolicy = RetentionPolicy.valueOf(retentionPolicyName);
    }

    return new JvmClass(
      jvmClassNode.getFlags(),
      jvmClassNode.getSignature(),
      jvmClassNode.getName(),
      jvmClassNode.getOutFilePath(),
      superFqName,
      outerFqName,
      interfaces,
      fields,
      methods,
      jvmClassNode.getAnnotations(),
      annotationTargets,
      retentionPolicy,
      jvmClassNode.getUsages()
    );
  }

  static JvmMethod readJvmMethod(DataInput in) throws IOException {
    ProtoMember protoMember = readProtoMember(in);

    // Read ArgTypes
    int argTypesCount = DataInputOutputUtil.readINT(in);
    List<TypeRepr> argTypes = new ArrayList<>();
    for (int i = 0; i < argTypesCount; i++) {
      argTypes.add(readTypeRepr(in));
    }

    // Read ParamAnnotations
    int paramAnnotationsCount = DataInputOutputUtil.readINT(in);
    Set<ParamAnnotation> paramAnnotations = new HashSet<>();
    for (int i = 0; i < paramAnnotationsCount; i++) {
      int paramIndex = DataInputOutputUtil.readINT(in);
      String typeName = in.readUTF();
      TypeRepr.ClassType paramType = new TypeRepr.ClassType(typeName);
      paramAnnotations.add(new ParamAnnotation(paramIndex, paramType));
    }

    // Read Exceptions
    int exceptionsCount = DataInputOutputUtil.readINT(in);
    String[] exceptions = new String[exceptionsCount];
    for (int i = 0; i < exceptionsCount; i++) {
      String exceptionName = in.readUTF();
      exceptions[i] = exceptionName;
    }

    String descriptor = in.readUTF();

    return new JvmMethod(protoMember.getFlags(),
                         protoMember.getSignature(),
                         protoMember.getName(),
                         descriptor,
                         protoMember.getAnnotations(),
                         paramAnnotations,
                         exceptions,
                         protoMember.getValue()
    );
  }

  private String getJvmMethodDescr(Iterable<TypeRepr> myArgTypes, TypeRepr type) {
    final StringBuilder buf = new StringBuilder();

    buf.append("(");

    for (TypeRepr t : myArgTypes) {
      buf.append(t.getDescriptor());
    }

    buf.append(")");
    buf.append(type.getDescriptor());

    return buf.toString();
  }

  static JvmField readJvmField(DataInput in) throws IOException {
    ProtoMember protoMember = readProtoMember(in);
    return new JvmField(protoMember.getFlags(), protoMember.getSignature(), protoMember.getName(), protoMember.getType().getDescriptor(),
                        protoMember.getAnnotations(), protoMember.getValue());
  }

  static ProtoMember readProtoMember(DataInput in) throws IOException {
    Proto proto = readProto(in);
    TypeRepr typeRepr = readTypeRepr(in);
    Object value = readValueObject(in);
    return new ProtoMember(proto.getFlags(), proto.getSignature(), proto.getName(), typeRepr, proto.getAnnotations(), value) {
      @Override
      public MemberUsage createUsage(String owner) {
        return null;
      }
    };
  }

  static TypeRepr readTypeRepr(DataInput in) throws IOException {
    String typeReprName = in.readUTF();
    switch (typeReprName) {
      case "PrimitiveType":
        String descriptor = in.readUTF();
        return new TypeRepr.PrimitiveType(descriptor);
      case "ClassType":
        String jvmName = in.readUTF();
        return new TypeRepr.ClassType(jvmName);
      case "ArrayType":
        String arrayDescriptor = in.readUTF();
        return new TypeRepr.ArrayType(TypeRepr.getType(arrayDescriptor));
      default:
        throw new SerializationException("Serialization error: Unexpected type for 'TypeRepr': " + typeReprName);
    }
  }

  static Object readValueObject(DataInput in) throws IOException {
    try {
      final byte tag = in.readByte();
      if (tag < 0) {
        final int length = DataInputOutputUtil.readINT(in);
        final DataDescriptor descriptor = DataDescriptor.findById((byte)-tag);
        final Object array = Array.newInstance(descriptor.getDataType(), length);
        for (int idx = 0; idx < length; idx++) {
          Array.set(array, idx, descriptor.load(in));
        }
        return array;
      }
      return DataDescriptor.findById(tag).load(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  static MethodUsage readMethodUsage(DataInput in) throws IOException {
    String className = in.readUTF();
    String name = in.readUTF();
    String descriptor = in.readUTF();
    return new MethodUsage(className, name, descriptor);
  }

  static ImportStaticMemberUsage readImportStaticMemberUsage(DataInput in) throws IOException {
    String className = in.readUTF();
    String name = in.readUTF();
    return new ImportStaticMemberUsage(className, name);
  }

  static ClassUsage readClassUsage(DataInput in) throws IOException {
    String className = in.readUTF();
    return new ClassUsage(className);
  }

  static ModuleUsage readModuleUsage(DataInput in) throws IOException {
    String moduleName = in.readUTF();
    return new ModuleUsage(moduleName);
  }

  static ImportStaticOnDemandUsage readImportStaticOnDemandUsage(DataInput in) throws IOException {
    String importedClassName = in.readUTF();
    return new ImportStaticOnDemandUsage(importedClassName);
  }

  static AnnotationUsage readAnnotationUsage(DataInput in) throws IOException {
    String classTypeJvmName = in.readUTF();
    int userArgNamesCount = DataInputOutputUtil.readINT(in);
    List<String> userArgNames = new ArrayList<>(userArgNamesCount);
    for (int i = 0; i < userArgNamesCount; i++) {
      String userArgName = in.readUTF();
      userArgNames.add(userArgName);
    }
    int targetsCount = DataInputOutputUtil.readINT(in);
    List<ElemType> targets = new ArrayList<>(targetsCount);
    for (int i = 0; i < targetsCount; i++) {
      ElemType elemType = readElemType(in);
      targets.add(elemType);
    }
    return new AnnotationUsage(new TypeRepr.ClassType(classTypeJvmName), userArgNames, targets);
  }

  static ElemType readElemType(DataInput in) throws IOException {
    String elemTypeName = in.readUTF();
    return ElemType.valueOf(elemTypeName);
  }

  static ClassAsGenericBoundUsage readClassAsGenericBoundUsage(DataInput in) throws IOException {
    return new ClassAsGenericBoundUsage(readClassUsage(in).getClassName());
  }

  static ClassExtendsUsage readClassExtendsUsage(DataInput in) throws IOException {
    return new ClassExtendsUsage(readClassUsage(in).getClassName());
  }

  static ClassNewUsage readClassNewUsage(DataInput in) throws IOException {
    return new ClassNewUsage(readClassUsage(in).getClassName());
  }

  static Usage readUsage(DataInput in) throws IOException {
    String usageClassName = in.readUTF();
    switch (usageClassName) {
      case "FieldAssignUsage":
        return readFieldAssignUsage(in);
      case "FieldUsage":
        return readFieldUsage(in);
      case "MethodUsage":
        return readMethodUsage(in);
      case "ImportStaticMemberUsage":
        return readImportStaticMemberUsage(in);
      case "ClassAsGenericBoundUsage":
        return readClassAsGenericBoundUsage(in);
      case "ClassExtendsUsage":
        return readClassExtendsUsage(in);
      case "ClassNewUsage":
        return readClassNewUsage(in);
      case "ClassUsage":
        return readClassUsage(in);
      case "AnnotationUsage":
        return readAnnotationUsage(in);
      case "ModuleUsage":
        return readModuleUsage(in);
      case "ImportStaticOnDemandUsage":
        return readImportStaticOnDemandUsage(in);
      default:
        throw new SerializationException("Unknown Usage class: " + usageClassName);
    }
  }

  static void writeJvmModule(JvmModule jvmModule, DataOutput out) throws IOException {
    writeJVMClassNode(jvmModule, out);
    out.writeUTF(jvmModule.getVersion());

    // Write Requires
    int requiresCount = 0;
    for (ModuleRequires moduleRequires : jvmModule.getRequires()) {
      requiresCount++;
    }
    DataInputOutputUtil.writeINT(out, requiresCount);
    for (ModuleRequires moduleRequires : jvmModule.getRequires()) {
      writeProto(moduleRequires, out);
      out.writeUTF(moduleRequires.getVersion());
    }

    // Write exports
    int exportsCount = 0;
    for (ModulePackage export : jvmModule.getExports()) {
      exportsCount++;
    }
    DataInputOutputUtil.writeINT(out, exportsCount);
    for (ModulePackage export : jvmModule.getExports()) {
      writeProto(export, out);

      // Write modules
      int modulesCount = 0;
      for (String module : export.getModules()) {
        modulesCount++;
      }
      DataInputOutputUtil.writeINT(out, modulesCount);
      for (String module : export.getModules()) {
        out.writeUTF(module);
      }
    }
  }

  static JvmModule readJvmModule(DataInput in) throws IOException {
    JVMClassNode jvmClassNode = readJvmClassNode(in);
    String version = in.readUTF();

    // Read Requires
    int requiresCount = DataInputOutputUtil.readINT(in);
    List<ModuleRequires> requiresList = new ArrayList<>();
    for (int i = 0; i < requiresCount; i++) {
      Proto proto = readProto(in);
      String moduleVersion = in.readUTF();
      requiresList.add(new ModuleRequires(proto.getFlags(), proto.getName(), moduleVersion));
    }

    // Read exports
    int exportsCount = DataInputOutputUtil.readINT(in);
    List<ModulePackage> exportsList = new ArrayList<>();
    for (int i = 0; i < exportsCount; i++) {
      Proto proto = readProto(in);

      // Read modules
      int modulesCount = DataInputOutputUtil.readINT(in);
      List<String> modulesList = new ArrayList<>();
      for (int j = 0; j < modulesCount; j++) {
        String module = in.readUTF();
        modulesList.add(module);
      }

      exportsList.add(new ModulePackage(proto.getName(), modulesList));
    }

    return new JvmModule(jvmClassNode.getFlags(),
                     jvmClassNode.getName(),
                     jvmClassNode.getOutFilePath(),
                     version,
                     requiresList,
                     exportsList,
                     jvmClassNode.getUsages());
  }
}
