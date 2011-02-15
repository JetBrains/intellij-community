package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 5:11
 * To change this template use File | Settings | File Templates.
 */
public class UsageRepr {
    private final static TypeRepr.AbstractType[] dummyAbstractType = new TypeRepr.AbstractType[0];

    private final static Map<Usage, Usage> map = new HashMap<Usage, Usage>();

    private static Usage getUsage(final Usage u) {
        final Usage r = map.get (u);

        if (r == null) {
            map.put (u, u);
            return u;
        }

        return r;
    }

    public static abstract class Usage implements RW.Writable {
        public abstract StringCache.S getOwner ();
    }

    public static abstract class FMUsage extends Usage {
        public final StringCache.S name;
        public final StringCache.S owner;

        @Override
        public StringCache.S getOwner () {
            return owner;
        }

        private FMUsage(final String n, final String o) {
            name = StringCache.get(n);
            owner = StringCache.get(o);
        }
    }

    public static class FieldUsage extends FMUsage {
        public final TypeRepr.AbstractType type;

        private FieldUsage (final String n, final String o, final String d) {
            super (n, o);
            type = TypeRepr.getType (d);
        }

         private FieldUsage(final BufferedReader r) {
            super (RW.readString(r), RW.readString(r));
            type = TypeRepr.reader.read(r);
        }

        public void write(final BufferedWriter w) {
            RW.writeln(w, "fieldUsage");
            RW.writeln(w, name.value);
            RW.writeln(w, owner.value);
            type.write(w);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FieldUsage that = (FieldUsage) o;

            if (type != null ? !type.equals(that.type) : that.type != null) return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result += result*31 + (name != null ? name.hashCode() : 0);

            return result * 31 + (owner != null ? owner.hashCode() : 0);
        }
    }

    public static class MethodUsage extends FMUsage {
        public final TypeRepr.AbstractType[] argumentTypes;
        public final TypeRepr.AbstractType returnType;

        private MethodUsage (final String n, final String o, final String d) {
            super (n, o);
            argumentTypes = TypeRepr.getType (Type.getArgumentTypes(d));
            returnType = TypeRepr.getType (Type.getReturnType(d));
        }

         private MethodUsage (final BufferedReader r) {
            super (RW.readString(r), RW.readString(r));
            argumentTypes = RW.readMany(r, TypeRepr.reader, new ArrayList<TypeRepr.AbstractType> ()).toArray(dummyAbstractType);
            returnType = TypeRepr.reader.read(r);
        }

        public void write(final BufferedWriter w) {
            RW.writeln(w, "methodUsage");
            RW.writeln(w, name.value);
            RW.writeln(w, owner.value);
            RW.writeln(w, argumentTypes, TypeRepr.fromAbstractType);
            returnType.write(w);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodUsage that = (MethodUsage) o;

            if (!Arrays.equals(argumentTypes, that.argumentTypes)) return false;
            if (returnType != null ? !returnType.equals(that.returnType) : that.returnType != null) return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = argumentTypes != null ? Arrays.hashCode(argumentTypes) : 0;
            result = 31 * result + (returnType != null ? returnType.hashCode() : 0);
            result += result*31 + (name != null ? name.hashCode() : 0);
            return result * 31 + (owner != null ? owner.hashCode() : 0);
        }
    }

    public static class ClassUsage extends Usage {
        final StringCache.S className;

        @Override
        public StringCache.S getOwner() {
            return className;
        }

        private ClassUsage (final String n) {
            className = StringCache.get (n);
        }

        private ClassUsage (final StringCache.S n) {
            className = n;
        }

        private ClassUsage (final BufferedReader r) {
            className = StringCache.get (RW.readString(r));
        }

        public void write(BufferedWriter w) {
            RW.writeln (w, "classUsage");
            RW.writeln (w, className.value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClassUsage that = (ClassUsage) o;

            if (className != null ? !className.equals(that.className) : that.className != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return className != null ? className.hashCode() : 0;
        }
    }

    public static Usage createFieldUsage(final String name, final String owner, final String descr) {
        return getUsage(new FieldUsage(name, owner, descr));
    }

    public static Usage createMethodUsage(final String name, final String owner, final String descr) {
        return getUsage(new MethodUsage(name, owner, descr));
    }

    public static Usage createClassUsage(final String name) {
        return getUsage(new ClassUsage (name));
    }

    public static Usage createClassUsage(final StringCache.S name) {
        return getUsage(new ClassUsage (name));
    }

    public static RW.Reader<Usage> reader = new RW.Reader<Usage>() {
        public Usage read(final BufferedReader r) {
            final String tag = RW.readString(r);

            if (tag.equals("classUsage")) {
                return getUsage(new ClassUsage (r));
            }
            else if (tag.equals("fieldUsage")) {
                return getUsage(new FieldUsage(r));
            }

            return getUsage(new MethodUsage(r));
        }
    };
}
