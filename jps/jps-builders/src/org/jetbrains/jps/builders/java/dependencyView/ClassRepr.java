/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.*;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * @author: db
 * Date: 01.02.11
 */
public class ClassRepr extends Proto {
  private final DependencyContext myContext;
  private final int myFileName;
  private final TypeRepr.ClassType mySuperClass;
  private final Set<TypeRepr.AbstractType> myInterfaces;
  private final Set<ElemType> myAnnotationTargets;
  private final RetentionPolicy myRetentionPolicy;

  private final Set<FieldRepr> myFields;
  private final Set<MethodRepr> myMethods;
  private final Set<UsageRepr.Usage> myUsages;

  private final int myOuterClassName;
  private final boolean myIsLocal;
  private final boolean myIsAnonymous;

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

  public boolean isAnonymous() {
    return myIsAnonymous;
  }

  public TypeRepr.ClassType getSuperClass() {
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

  public boolean isInterface() {
    return (access & Opcodes.ACC_INTERFACE) != 0;
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
        if ((d & Difference.SUPERCLASS) <= 0) {
          return false;
        }
        final String pastSuperName = myContext.getValue(((ClassRepr)past).mySuperClass.className);
        return "java/lang/Object".equals(pastSuperName);
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

    result[0] = mySuperClass.className;

    int i = 1;
    for (TypeRepr.AbstractType t : myInterfaces) {
      result[i++] = ((TypeRepr.ClassType)t).className;
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
                   final Set<FieldRepr> f,
                   final Set<MethodRepr> m,
                   final Set<ElemType> targets,
                   final RetentionPolicy policy,
                   final int outerClassName,
                   final boolean localClassFlag,
                   final boolean anonymousClassFlag,
                   final Set<UsageRepr.Usage> usages) {
    super(a, sig, n);
    this.myContext = context;
    myFileName = fn;
    mySuperClass = TypeRepr.createClassType(context, sup);
    myInterfaces = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, i, new THashSet<TypeRepr.AbstractType>(1));
    myFields = f;
    myMethods = m;
    this.myAnnotationTargets = targets;
    this.myRetentionPolicy = policy;
    this.myOuterClassName = outerClassName;
    this.myIsLocal = localClassFlag;
    this.myIsAnonymous = anonymousClassFlag;
    this.myUsages = usages;
  }

  public ClassRepr(final DependencyContext context, final DataInput in) {
    super(in);
    try {
      this.myContext = context;
      myFileName = DataInputOutputUtil.readINT(in);
      mySuperClass = (TypeRepr.ClassType)TypeRepr.externalizer(context).read(in);
      myInterfaces = (Set<TypeRepr.AbstractType>)RW.read(TypeRepr.externalizer(context), new THashSet<TypeRepr.AbstractType>(1), in);
      myFields = (Set<FieldRepr>)RW.read(FieldRepr.externalizer(context), new THashSet<FieldRepr>(), in);
      myMethods = (Set<MethodRepr>)RW.read(MethodRepr.externalizer(context), new THashSet<MethodRepr>(), in);
      myAnnotationTargets = (Set<ElemType>)RW.read(UsageRepr.AnnotationUsage.elementTypeExternalizer, EnumSet.noneOf(ElemType.class), in);

      final String s = RW.readUTF(in);

      myRetentionPolicy = s.length() == 0 ? null : RetentionPolicy.valueOf(s);

      myOuterClassName = DataInputOutputUtil.readINT(in);
      int flags = DataInputOutputUtil.readINT(in);
      myIsLocal = (flags & LOCAL_MASK) != 0;
      myIsAnonymous = (flags & ANONYMOUS_MASK) != 0;
      myUsages =(Set<UsageRepr.Usage>)RW.read(UsageRepr.externalizer(context), new THashSet<UsageRepr.Usage>(), in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static final int LOCAL_MASK = 1;
  private static final int ANONYMOUS_MASK = 2;

  @Override
  public void save(final DataOutput out) {
    try {
      super.save(out);
      DataInputOutputUtil.writeINT(out, myFileName);
      mySuperClass.save(out);
      RW.save(myInterfaces, out);
      RW.save(myFields, out);
      RW.save(myMethods, out);
      RW.save(myAnnotationTargets, UsageRepr.AnnotationUsage.elementTypeExternalizer, out);
      RW.writeUTF(out, myRetentionPolicy == null ? "" : myRetentionPolicy.toString());
      DataInputOutputUtil.writeINT(out, myOuterClassName);
      DataInputOutputUtil.writeINT(out, (myIsLocal ? LOCAL_MASK:0) | (myIsAnonymous ? ANONYMOUS_MASK : 0));

      RW.save(myUsages, UsageRepr.externalizer(myContext), out);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
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
    final String strValue = myContext.getValue(name);
    return strValue != null? getPackageName(strValue) : null;
  }

  public String getShortName() {
    final String strValue = myContext.getValue(name);
    return strValue != null? getShortName(strValue) : null;
  }

  @NotNull
  public static String getPackageName(@NotNull final String raw) {
    final int index = raw.lastIndexOf('/');

    if (index == -1) {
      return "";
    }

    return raw.substring(0, index);
  }

  @NotNull
  public static String getShortName(@NotNull final String fqName) {
    final int index = fqName.lastIndexOf('/');

    if (index == -1) {
      return fqName;
    }

    return fqName.substring(index + 1);
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
      public void save(@NotNull final DataOutput out, final ClassRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public ClassRepr read(@NotNull final DataInput in) throws IOException {
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
    stream.print("      Anonymous class: ");
    stream.println(myIsAnonymous);

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
      catch (final IOException e) {
        throw new BuildDataCorruptedException(e);
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
