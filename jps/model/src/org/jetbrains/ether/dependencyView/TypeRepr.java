package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 3:54
 * To change this template use File | Settings | File Templates.
 */
class TypeRepr {
  private TypeRepr () {

  }

  public static abstract class AbstractType implements RW.Writable {
    public abstract void updateClassUsages(DependencyContext.S owner, UsageRepr.Cluster s);
    public abstract String getDescr();
  }

  public static class PrimitiveType extends AbstractType {
    public final DependencyContext.S type;

    @Override
    public String getDescr() {
      return type.getValue();
    }

    @Override
    public void updateClassUsages(final DependencyContext.S owner, final UsageRepr.Cluster s) {

    }

    public void write(final BufferedWriter w) {
      RW.writeln(w, "primitive");
      RW.writeln(w, type.getValue());
    }

    PrimitiveType(final DependencyContext.S type) {
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final PrimitiveType that = (PrimitiveType)o;

      return type.equals(that.type);
    }

    @Override
    public int hashCode() {
      return type.hashCode();
    }
  }

  public static class ArrayType extends AbstractType {
    public final AbstractType elementType;

    public AbstractType getDeepElementType() {
      AbstractType current = this;

      while (current instanceof ArrayType) {
        current = ((ArrayType)current).elementType;
      }

      return current;
    }

    @Override
    public String getDescr() {
      return "[" + elementType.getDescr();
    }

    @Override
    public void updateClassUsages(final DependencyContext.S owner, final UsageRepr.Cluster s) {
      elementType.updateClassUsages(owner, s);
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

    public void write(BufferedWriter w) {
      RW.writeln(w, "array");
      elementType.write(w);
    }
  }

  public static class ClassType extends AbstractType {
    public final DependencyContext.S className;
    public final AbstractType[] typeArgs;

    @Override
    public String getDescr() {
      return "L" + className.getValue() + ";";
    }

    @Override
    public void updateClassUsages(final DependencyContext.S owner, final UsageRepr.Cluster s) {
      s.addUsage(owner, UsageRepr.createClassUsage(className));
    }

    ClassType (final DependencyContext context, final BufferedReader r) {
      className = context.get(RW.readString(r));
      final Collection<AbstractType> args = RW.readMany(r, reader (context), new LinkedList<AbstractType>());
      typeArgs = args.toArray(new AbstractType[args.size()]);
    }

    ClassType(final DependencyContext.S className) {
      this.className = className;
      typeArgs = new AbstractType[0];
    }

    ClassType(final DependencyContext.S className, final AbstractType[] typeArgs) {
      this.className = className;
      this.typeArgs = typeArgs;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ClassType classType = (ClassType)o;

      if (className != null ? !className.equals(classType.className) : classType.className != null) return false;
      if (!Arrays.equals(typeArgs, classType.typeArgs)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = className != null ? className.hashCode() : 0;
      result = 31 * result + (typeArgs != null ? Arrays.hashCode(typeArgs) : 0);
      return result;
    }

    public void write(BufferedWriter w) {
      RW.writeln(w, "class");
      RW.writeln(w, className.getValue());
      RW.writeln(w, typeArgs);
    }
  }

  public static Collection<AbstractType> createClassType (final DependencyContext context, final String[] args, final Collection<AbstractType> acc) {
    if (args != null) {
      for (String a : args) {
        acc.add(createClassType(context.get(a)));    
      }      
    }
    
    return acc;  
  }
  
  public static Collection<AbstractType> createClassType (final DependencyContext context, final Collection<String> args, final Collection<AbstractType> acc) {
      if (args != null) {
        for (String a : args) {
          acc.add(createClassType(context.get(a)));    
        }      
      }
      
      return acc;  
    }
  
  public static ClassType createClassType(final DependencyContext.S s) {
    return (ClassType)getType(new ClassType(s));
  }
    
  private static final Map<AbstractType, AbstractType> map = new HashMap<AbstractType, AbstractType>();

  private static AbstractType getType(final AbstractType t) {
    final AbstractType r = map.get(t);

    if (r != null) {
      return r;
    }

    map.put(t, t);

    return t;
  }

  public static AbstractType getType(final DependencyContext context, final DependencyContext.S descr) {
    final Type t = Type.getType(descr.getValue());

    switch (t.getSort()) {
      case Type.OBJECT:
        return getType(new ClassType(context.get(t.getClassName().replaceAll("\\.", "/"))));

      case Type.ARRAY:
        return getType(new ArrayType(getType(context, t.getElementType())));

      default:
        return getType(new PrimitiveType(descr));
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

  public static AbstractType[] getType(final DependencyContext context, final String[] t) {
    if (t == null) {
      return null;
    }

    final AbstractType[] types = new AbstractType[t.length];

    for (int i = 0; i < types.length; i++) {
      types[i] = getType(context, Type.getType(t[i]));
    }

    return types;
  }

  public static RW.Reader<AbstractType> reader (final DependencyContext context) {
    return new RW.Reader<AbstractType>() {
      public AbstractType read(final BufferedReader r) {
        AbstractType elementType = null;
        int level = 0;

        while (true) {
          final String tag = RW.readString(r);

          if (tag.equals("primitive")) {
            elementType = getType(new PrimitiveType(context.get(RW.readString(r))));
            break;
          }

          if (tag.equals("class")) {
            elementType = getType(new ClassType(context, r));
            break;
          }

          if (tag.equals("array")) {
            level++;
          }
        }

        for (int i = 0; i < level; i++) {
          elementType = getType(new ArrayType(elementType));
        }

        return elementType;
      }
    };
  }

  public static RW.ToWritable<AbstractType> fromAbstractType = new RW.ToWritable<AbstractType>() {
    public RW.Writable convert(final AbstractType x) {
      return new RW.Writable() {
        public void write(final BufferedWriter w) {
          x.write(w);
        }
      };
    }
  };
}
