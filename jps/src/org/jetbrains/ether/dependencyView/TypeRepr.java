package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 3:54
 * To change this template use File | Settings | File Templates.
 */
public class TypeRepr {

    public static abstract class AbstractType implements RW.Writable {
    }

    public static class PrimitiveType extends AbstractType {
        public final StringCache.S type;

        public void write(final BufferedWriter w) {
            RW.writeln(w, "primitive");
            RW.writeln(w, type.value);
        }

        PrimitiveType(final String type) {
            this.type = StringCache.get(type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PrimitiveType that = (PrimitiveType) o;

            if (type != null ? !type.equals(that.type) : that.type != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return type != null ? type.hashCode() : 0;
        }
    }

    public static class ArrayType extends AbstractType {
        public final AbstractType elementType;

        ArrayType(final AbstractType elementType) {
            this.elementType = elementType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ArrayType arrayType = (ArrayType) o;

            if (elementType != null ? !elementType.equals(arrayType.elementType) : arrayType.elementType != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            return elementType != null ? elementType.hashCode() : 0;
        }

        public void write(BufferedWriter w) {
            RW.writeln(w, "array");
            elementType.write(w);
        }
    }

    public static class ClassType extends AbstractType {
        public final StringCache.S className;

        ClassType(final String className) {
            this.className = StringCache.get(className);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClassType classType = (ClassType) o;

            if (className != null ? !className.equals(classType.className) : classType.className != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return className != null ? className.hashCode() : 0;
        }

        public void write(BufferedWriter w) {
            RW.writeln(w, "class");
            RW.writeln(w, className.value);
        }
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

    public static AbstractType getType(final String descr) {
        final Type t = Type.getType(descr);

        switch (t.getSort()) {
            case Type.OBJECT:
                return getType(new ClassType(t.getClassName()));

            case Type.ARRAY:
                return getType(new ArrayType(getType(t.getElementType())));

            default:
                return getType(new PrimitiveType(descr));
        }
    }

    public static AbstractType getType (final Type t) {
        return getType (t.getDescriptor());
    }

    public static AbstractType[] getType (final Type[] t) {
        final AbstractType[] r = new AbstractType[t.length];

        for (int i = 0; i<r.length; i++)
            r[i] = getType (t[i]);

        return r;
    }

    public static RW.Reader<AbstractType> reader = new RW.Reader<AbstractType>() {
        public AbstractType read(final BufferedReader r) {
            AbstractType elementType = null;
            int level = 0;

            while (true) {
                final String tag = RW.readString(r);

                if (tag.equals("primitive")) {
                    elementType = getType(new PrimitiveType(RW.readString(r)));
                    break;
                }

                if (tag.equals("class")) {
                    elementType = getType(new ClassType(RW.readString(r)));
                    break;
                }

                if (tag.equals("array")) {
                    level++;
                }
            }

            for (int i = 0; i<level; i++) {
                elementType = getType (new ArrayType (elementType));
            }

            return elementType;
        }
    };

    public static RW.ToWritable<AbstractType> fromAbstractType = new RW.ToWritable<AbstractType> () {
        public RW.Writable convert(final AbstractType x) {
            return new RW.Writable() {
                public void write(final BufferedWriter w) {
                    x.write(w);
                }
            };
        }
    };
}
