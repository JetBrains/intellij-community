package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.asm4.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 3:54
 * To change this template use File | Settings | File Templates.
 */
class TypeRepr {
  private static final byte PRIMITIVE_TYPE = 0x0;
  private static final byte CLASS_TYPE = 0x1;
  private static final byte ARRAY_TYPE = 0x2;

  private TypeRepr() {

  }

  interface AbstractType extends RW.Savable {
    void updateClassUsages(DependencyContext context, int owner, Set<UsageRepr.Usage> s);
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
    public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {

    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(PRIMITIVE_TYPE);
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
    public final AbstractType myElementType;

    public AbstractType getDeepElementType() {
      AbstractType current = this;

      while (current instanceof ArrayType) {
        current = ((ArrayType)current).myElementType;
      }

      return current;
    }

    @Override
    public String getDescr(final DependencyContext context) {
      return "[" + myElementType.getDescr(context);
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
      myElementType.updateClassUsages(context, owner, s);
    }

    ArrayType(final AbstractType elementType) {
      this.myElementType = elementType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ArrayType arrayType = (ArrayType)o;

      return myElementType.equals(arrayType.myElementType);
    }

    @Override
    public int hashCode() {
      return myElementType.hashCode();
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(ARRAY_TYPE);
        myElementType.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class ClassType implements AbstractType {
    public final int myClassName;
    public final AbstractType[] myTypeArgs;

    @Override
    public String getDescr(final DependencyContext context) {
      return "L" + context.getValue(myClassName) + ";";
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
      s.add(UsageRepr.createClassUsage(context, myClassName));
    }

    ClassType(final int className) {
      this.myClassName = className;
      myTypeArgs = new AbstractType[0];
    }

    ClassType(final DependencyContext context, final DataInput in) {
      try {
        myClassName = in.readInt();
        final int size = in.readInt();
        myTypeArgs = new AbstractType[size];

        final DataExternalizer<AbstractType> externalizer = externalizer(context);

        for (int i = 0; i < size; i++) {
          myTypeArgs[i] = externalizer.read(in);
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

      if (myClassName != classType.myClassName) return false;
      if (!Arrays.equals(myTypeArgs, classType.myTypeArgs)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myClassName;
      result = 31 * result + (myTypeArgs != null ? Arrays.hashCode(myTypeArgs) : 0);
      return result;
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_TYPE);
        out.writeInt(myClassName);
        out.writeInt(myTypeArgs.length);
        for (AbstractType t : myTypeArgs) {
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
          final byte tag = in.readByte();
          switch (tag) {
            case PRIMITIVE_TYPE:
              elementType = context.getType(new PrimitiveType(in));
              break loop;

            case CLASS_TYPE:
              elementType = context.getType(new ClassType(context, in));
              break loop;

            case ARRAY_TYPE:
              level++;
              break;

            default :
              System.out.println("Unknown type!");
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
