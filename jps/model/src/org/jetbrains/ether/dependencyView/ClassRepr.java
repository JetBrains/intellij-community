package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import groovyjarjarasm.asm.Opcodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.RW;

import java.io.*;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:54
 * To change this template use File | Settings | File Templates.
 */
public class ClassRepr extends Proto {
  private final DependencyContext myContext;
  private final int myFileName;
  private final TypeRepr.AbstractType mySuperClass;
  private final Set<TypeRepr.AbstractType> myInterfaces;
  private final Set<ElemType> myAnnotationTargets;
  private final RetentionPolicy myRetentionPolicy;

  private final Set<FieldRepr> myFields;
  private final Set<MethodRepr> myMethods;
  private final Set<UsageRepr.Usage> myUsages;

  private final int myOuterClassName;
  private final boolean myIsLocal;

  public Set<MethodRepr> getMethods() {
    return myMethods;
  }

  public Set<FieldRepr> getFields() {
    return myFields;
  }

  public int getOuterClassName() {
    return myOuterClassName;
  }

  public boolean isLocal() {
    return myIsLocal;
  }

  public TypeRepr.AbstractType getSuperClass() {
    return mySuperClass;
  }

  public RetentionPolicy getRetentionPolicy() {
    return myRetentionPolicy;
  }

  public Set<UsageRepr.Usage> getUsages() {
    return myUsages;
  }

  public boolean addUsage(final UsageRepr.Usage usage) {
    return myUsages.add(usage);
  }

  public abstract static class Diff extends Difference {
    public abstract Specifier<TypeRepr.AbstractType> interfaces();

    public abstract Specifier<FieldRepr> fields();

    public abstract Specifier<MethodRepr> methods();

    public abstract Specifier<ElemType> targets();

    public abstract boolean retentionChanged();

    public abstract boolean extendsAdded();

    public boolean no() {
      return base() == NONE &&
             interfaces().unchanged() &&
             fields().unchanged() &&
             methods().unchanged() &&
             targets().unchanged() &&
             !retentionChanged();
    }
  }

  public Diff difference(final Proto past) {
    final ClassRepr pastClass = (ClassRepr)past;
    final Difference diff = super.difference(past);
    int base = diff.base();

    if (!mySuperClass.equals(pastClass.mySuperClass)) {
      base |= Difference.SUPERCLASS;
    }

    if (!myUsages.equals(pastClass.myUsages)) {
      base |= Difference.USAGES;
    }
    final int d = base;

    return new Diff() {
      @Override
      public boolean extendsAdded() {
        final String pastSuperName = myContext.getValue(((TypeRepr.ClassType)((ClassRepr)past).mySuperClass).myClassName);
        return (d & Difference.SUPERCLASS) > 0 && pastSuperName.equals("java/lang/Object");
      }

      @Override
      public boolean packageLocalOn() {
        return diff.packageLocalOn();
      }

      @Override
      public int addedModifiers() {
        return diff.addedModifiers();
      }

      @Override
      public int removedModifiers() {
        return diff.removedModifiers();
      }

      @Override
      public Difference.Specifier<TypeRepr.AbstractType> interfaces() {
        return Difference.make(pastClass.myInterfaces, myInterfaces);
      }

      @Override
      public Difference.Specifier<FieldRepr> fields() {
        return Difference.make(pastClass.myFields, myFields);
      }

      @Override
      public Difference.Specifier<MethodRepr> methods() {
        return Difference.make(pastClass.myMethods, myMethods);
      }

      @Override
      public Specifier<ElemType> targets() {
        return Difference.make(pastClass.myAnnotationTargets, myAnnotationTargets);
      }

      @Override
      public boolean retentionChanged() {
        return !((myRetentionPolicy == null && pastClass.myRetentionPolicy == RetentionPolicy.CLASS) ||
                 (myRetentionPolicy == RetentionPolicy.CLASS && pastClass.myRetentionPolicy == null) ||
                 (myRetentionPolicy == pastClass.myRetentionPolicy));
      }

      @Override
      public int base() {
        return d;
      }

      @Override
      public boolean hadValue() {
        return false;
      }

      @Override
      public boolean weakedAccess() {
        return diff.weakedAccess();
      }
    };
  }

  @NotNull
  public int[] getSupers() {
    final int[] result = new int[myInterfaces.size() + 1];

    result[0] = ((TypeRepr.ClassType)mySuperClass).myClassName;

    int i = 1;
    for (TypeRepr.AbstractType t : myInterfaces) {
      result[i++] = ((TypeRepr.ClassType)t).myClassName;
    }

    return result;
  }

  public void updateClassUsages(final DependencyContext context, final Set<UsageRepr.Usage> s) {
    mySuperClass.updateClassUsages(context, name, s);

    for (TypeRepr.AbstractType t : myInterfaces) {
      t.updateClassUsages(context, name, s);
    }

    for (MethodRepr m : myMethods) {
      m.updateClassUsages(context, name, s);
    }

    for (FieldRepr f : myFields) {
      f.updateClassUsages(context, name, s);
    }
  }

  public ClassRepr(final DependencyContext context, final int a, final int fn, final int n, final int sig,
                   final int sup,
                   final String[] i,
                   final Collection<String> ns,
                   final Set<FieldRepr> f,
                   final Set<MethodRepr> m,
                   final Set<ElemType> targets,
                   final RetentionPolicy policy,
                   final int outerClassName,
                   final boolean localClassFlag,
                   final Set<UsageRepr.Usage> usages) {
    super(a, sig, n);
    this.myContext = context;
    myFileName = fn;
    mySuperClass = TypeRepr.createClassType(context, sup);
    myInterfaces = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, i, new HashSet<TypeRepr.AbstractType>());
    myFields = f;
    myMethods = m;
    this.myAnnotationTargets = targets;
    this.myRetentionPolicy = policy;
    this.myOuterClassName = outerClassName;
    this.myIsLocal = localClassFlag;
    this.myUsages = usages;
  }

  public ClassRepr(final DependencyContext context, final DataInput in) {
    super(in);
    try {
      this.myContext = context;
      myFileName = in.readInt();
      mySuperClass = TypeRepr.externalizer(context).read(in);
      myInterfaces = (Set<TypeRepr.AbstractType>)RW.read(TypeRepr.externalizer(context), new HashSet<TypeRepr.AbstractType>(), in);
      myFields = (Set<FieldRepr>)RW.read(FieldRepr.externalizer(context), new HashSet<FieldRepr>(), in);
      myMethods = (Set<MethodRepr>)RW.read(MethodRepr.externalizer(context), new HashSet<MethodRepr>(), in);
      myAnnotationTargets = (Set<ElemType>)RW.read(UsageRepr.AnnotationUsage.elementTypeExternalizer, EnumSet.noneOf(ElemType.class), in);

      final String s = in.readUTF();

      myRetentionPolicy = s.length() == 0 ? null : RetentionPolicy.valueOf(s);

      myOuterClassName = in.readInt();
      myIsLocal = in.readBoolean();
      myUsages =(Set<UsageRepr.Usage>)RW.read(UsageRepr.externalizer(context), new HashSet<UsageRepr.Usage>(), in);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    try {
      super.save(out);
      out.writeInt(myFileName);
      mySuperClass.save(out);
      RW.save(myInterfaces, out);
      RW.save(myFields, out);
      RW.save(myMethods, out);
      RW.save(myAnnotationTargets, UsageRepr.AnnotationUsage.elementTypeExternalizer, out);
      out.writeUTF(myRetentionPolicy == null ? "" : myRetentionPolicy.toString());
      out.writeInt(myOuterClassName);
      out.writeBoolean(myIsLocal);
      RW.save(myUsages, UsageRepr.externalizer(myContext), out);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isAnnotation() {
    return (access & Opcodes.ACC_ANNOTATION) > 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ClassRepr classRepr = (ClassRepr)o;

    if (myFileName != classRepr.myFileName) return false;
    if (name != classRepr.name) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * myFileName + name;
  }

  public UsageRepr.Usage createUsage() {
    return UsageRepr.createClassUsage(myContext, name);
  }

  public String getPackageName() {
    return getPackageName(name);
  }

  public String getPackageName(final int s) {
    return getPackageName(myContext.getValue(s));
  }

  @NotNull
  public static String getPackageName(final String raw) {
    final int index = raw.lastIndexOf('/');

    if (index == -1) {
      return "";
    }

    return raw.substring(0, index);
  }

  @Nullable
  public FieldRepr findField(final int name) {
    for (FieldRepr f : myFields) {
      if (f.name == name) {
        return f;
      }
    }

    return null;
  }

  @NotNull
  public Collection<MethodRepr> findMethods(final MethodRepr.Predicate p) {
    final Collection<MethodRepr> result = new LinkedList<MethodRepr>();

    for (MethodRepr mm : myMethods) {
      if (p.satisfy(mm)) {
        result.add(mm);
      }
    }

    return result;
  }

  public static DataExternalizer<ClassRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<ClassRepr>() {
      @Override
      public void save(final DataOutput out, final ClassRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public ClassRepr read(final DataInput in) throws IOException {
        return new ClassRepr(context, in);
      }
    };
  }

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    super.toStream(context, stream);

    stream.print("      Filename   : ");
    stream.println(context.getValue(myFileName));

    stream.print("      Superclass : ");
    stream.println(mySuperClass == null ? "<null>" : mySuperClass.getDescr(context));

    stream.print("      Interfaces : ");
    final TypeRepr.AbstractType[] is = myInterfaces.toArray(new TypeRepr.AbstractType[myInterfaces.size()]);
    Arrays.sort(is, new Comparator<TypeRepr.AbstractType>() {
      @Override
      public int compare(final TypeRepr.AbstractType o1, final TypeRepr.AbstractType o2) {
        return o1.getDescr(context).compareTo(o2.getDescr(context));
      }
    });
    for (final TypeRepr.AbstractType t : is) {
      stream.print(t.getDescr(context));
      stream.print(" ");
    }
    stream.println();

    stream.print("      Targets    : ");
    final ElemType[] es = myAnnotationTargets.toArray(new ElemType[myAnnotationTargets.size()]);
    Arrays.sort(es);
    for (final ElemType e : es) {
      stream.print(e);
      stream.print("; ");
    }
    stream.println();

    stream.print("      Policy     : ");
    stream.println(myRetentionPolicy);

    stream.print("      Outer class: ");
    stream.println(context.getValue(myOuterClassName));

    stream.print("      Local class: ");
    stream.println(myIsLocal);

    stream.println("      Fields:");
    final FieldRepr[] fs = myFields.toArray(new FieldRepr[myFields.size()]);
    Arrays.sort(fs, new Comparator<FieldRepr>() {
      @Override
      public int compare(final FieldRepr o1, final FieldRepr o2) {
        if (o1.name == o2.name) {
          return o1.myType.getDescr(context).compareTo(o2.myType.getDescr(context));
        }

        return context.getValue(o1.name).compareTo(context.getValue(o2.name));
      }
    });
    for (final FieldRepr f : fs) {
      f.toStream(context, stream);
    }
    stream.println("      End Of Fields");

    stream.println("      Methods:");
    final MethodRepr[] ms = myMethods.toArray(new MethodRepr[myMethods.size()]);
    Arrays.sort(ms, new Comparator<MethodRepr>() {
      @Override
      public int compare(final MethodRepr o1, final MethodRepr o2) {
        if (o1.name == o2.name) {
          final String d1 = o1.myType.getDescr(context);
          final String d2 = o2.myType.getDescr(context);

          final int c = d1.compareTo(d2);

          if (c == 0) {
            final int l1 = o1.myArgumentTypes.length;
            final int l2 = o2.myArgumentTypes.length;

            if (l1 == l2) {
              for (int i = 0; i<l1; i++) {
                final String d11 = o1.myArgumentTypes[i].getDescr(context);
                final String d22 = o2.myArgumentTypes[i].getDescr(context);

                final int cc = d11.compareTo(d22);

                if (cc != 0) {
                  return cc;
                }
              }

              return 0;
            }

            return l1 -l2;
          }

          return c;
        }

        return context.getValue(o1.name).compareTo(context.getValue(o2.name));
      }
    });
    for (final MethodRepr m : ms) {
      m.toStream(context, stream);
    }
    stream.println("      End Of Methods");

    stream.println("      Usages:");

    final List<String> usages = new LinkedList<String>();

    for (final UsageRepr.Usage u : myUsages) {
      final ByteArrayOutputStream bas = new ByteArrayOutputStream();

      u.toStream(myContext, new PrintStream(bas));

      try {
        bas.close();
      }
      catch (final Exception e) {
        throw new RuntimeException(e);
      }

      usages.add(bas.toString());
    }

    Collections.sort(usages);

    for (final String s : usages) {
      stream.println(s);
    }

    stream.println("      End Of Usages");
  }
}
