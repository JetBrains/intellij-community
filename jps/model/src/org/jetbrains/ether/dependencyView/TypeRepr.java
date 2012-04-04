package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.asm4.Type;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 3:54
 * To change this template use File | Settings | File Templates.
 */
class TypeRepr {
  private final static int PRIMITIVE_TYPE = 0;
  private final static int CLASS_TYPE = 1;
  private final static int ARRAY_TYPE = 2;

  private TypeRepr() {

  }

  interface AbstractType extends RW.Savable {
    void updateClassUsages(DependencyContext context, int owner, UsageRepr.Cluster s);
    String getDescr(DependencyContext context);
    void save(DataOutput out);
  }

  public static class PrimitiveType implements AbstractType {
    public final int myType;

    @Override
    public String getDescr(final DependencyContext context) {
      return context.getValue(myType);
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final UsageRepr.Cluster s) {

    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(PRIMITIVE_TYPE);
        out.writeInt(myType);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    PrimitiveType(final int type) {
      this.myType = type;
    }

    PrimitiveType(final DataInput in) {
      try {
        myType = in.readInt();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final PrimitiveType that = (PrimitiveType)o;

      return myType == that.myType;
    }

    @Override
    public int hashCode() {
      return myType;
    }
  }

  public static class ArrayType implements AbstractType {
    public final AbstractType elementType;

    public AbstractType getDeepElementType() {
      AbstractType current = this;

      while (current instanceof ArrayType) {
        current = ((ArrayType)current).elementType;
      }

      return current;
    }

    @Override
    public String getDescr(final DependencyContext context) {
      return "[" + elementType.getDescr(context);
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final UsageRepr.Cluster s) {
      elementType.updateClassUsages(context, owner, s);
    }

    ArrayType(final AbstractType elementType) {
      this.elementType = elementType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ArrayType arrayType = (ArrayType)o;

      return elementType.equals(arrayType.elementType);
    }

    @Override
    public int hashCode() {
      return elementType.hashCode();
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(ARRAY_TYPE);
        elementType.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class ClassType implements AbstractType {
    public final int className;
    public final AbstractType[] typeArgs;

    @Override
    public String getDescr(final DependencyContext context) {
      return "L" + context.getValue(className) + ";";
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final UsageRepr.Cluster s) {
      s.addUsage(owner, UsageRepr.createClassUsage(context, className));
    }

    ClassType(final int className) {
      this.className = className;
      typeArgs = new AbstractType[0];
    }

    ClassType(final DependencyContext context, final DataInput in) {
      try {
        className = in.readInt();
        final int size = in.readInt();
        typeArgs = new AbstractType[size];

        final DataExternalizer<AbstractType> externalizer = externalizer(context);

        for (int i = 0; i < size; i++) {
          typeArgs[i] = externalizer.read(in);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ClassType classType = (ClassType)o;

      if (className != classType.className) return false;
      if (!Arrays.equals(typeArgs, classType.typeArgs)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = className;
      result = 31 * result + (typeArgs != null ? Arrays.hashCode(typeArgs) : 0);
      return result;
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeInt(CLASS_TYPE);
        out.writeInt(className);
        out.writeInt(typeArgs.length);
        for (AbstractType t : typeArgs) {
          t.save(out);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Collection<AbstractType> createClassType(final DependencyContext context,
                                                         final String[] args,
                                                         final Collection<AbstractType> acc) {
    if (args != null) {
      for (String a : args) {
        acc.add(createClassType(context, context.get(a)));
      }
    }

    return acc;
  }

  public static Collection<AbstractType> createClassType(final DependencyContext context,
                                                         final Collection<String> args,
                                                         final Collection<AbstractType> acc) {
    if (args != null) {
      for (String a : args) {
        acc.add(createClassType(context, context.get(a)));
      }
    }

    return acc;
  }

  public static ClassType createClassType(final DependencyContext context, final int s) {
    return (ClassType)context.getType(new ClassType(s));
  }

  public static AbstractType getType(final DependencyContext context, final int descr) {
    final Type t = Type.getType(context.getValue(descr));

    switch (t.getSort()) {
      case Type.OBJECT:
        return context.getType(new ClassType(context.get(StringUtil.replaceChar(t.getClassName(), '.', '/'))));

      case Type.ARRAY:
        return context.getType(new ArrayType(getType(context, t.getElementType())));

      default:
        return context.getType(new PrimitiveType(descr));
    }
  }

  public static AbstractType getType(final DependencyContext context, final Type t) {
    return getType(context, context.get(t.getDescriptor()));
  }

  public static AbstractType[] getType(final DependencyContext context, final Type[] t) {
    final AbstractType[] r = new AbstractType[t.length];

    for (int i = 0; i < r.length; i++) {
      r[i] = getType(context, t[i]);
    }

    return r;
  }

  public static DataExternalizer<AbstractType> externalizer(final DependencyContext context) {
    return new DataExternalizer<AbstractType>() {
      @Override
      public void save(final DataOutput out, final AbstractType value) throws IOException {
        value.save(out);
      }

      @Override
      public AbstractType read(final DataInput in) throws IOException {
        AbstractType elementType;
        int level = 0;

        loop:
        while (true) {
          switch (in.readInt()) {
            case PRIMITIVE_TYPE:
              elementType = context.getType(new PrimitiveType(in));
              break loop;

            case CLASS_TYPE:
              elementType = context.getType(new ClassType(context, in));
              break loop;

            case ARRAY_TYPE:
              level++;
              break;
          }
        }

        for (int i = 0; i < level; i++) {
          elementType = context.getType(new ArrayType(elementType));
        }

        return elementType;
      }
    };
  }
}
