import java.util.List;

class IDEA80386 {
  void foo(Class<List> listClass) {
    Class<? extends List<?>> cls = <error descr="Incompatible types. Found: 'java.lang.Class<java.util.List>', required: 'java.lang.Class<? extends java.util.List<?>>'">listClass</error>;
    Class < ?extends List > cls1 = listClass;
    Class<? extends List<? extends List<?>>> cls2 = <error descr="Incompatible types. Found: 'java.lang.Class<java.util.List>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">listClass</error>;
    Class<? super List<?>> clsS = listClass;
    Class<? super List> clsS1 = listClass;
  }

  void fooE(Class<? extends List> listClass) {
    Class<? extends List<?>> cls = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? extends java.util.List<?>>'">listClass</error>;
    Class<? extends List> cls1 = listClass;
    Class<? extends List<? extends List<?>>> cls2 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">listClass</error>;
    Class<? super List<?>> clsS = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? super java.util.List<?>>'">listClass</error>;
    Class<? super List> clsS1 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? super java.util.List>'">listClass</error>;
  }

  void fooS(Class<? super List> listClass) {
    Class<? extends List<?>> cls1 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? super java.util.List>>', required: 'java.lang.Class<? extends java.util.List<?>>'">listClass</error>;
    Class<? extends List<? extends List<?>>> cls2 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<? super java.util.List>>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">listClass</error>;
    Class<? super List<?>> clsS = listClass;
    Class<? super List> clsS1 = listClass;
  }

  void fooU(Class<?> listClass) {
    Class<? extends List<?>> cls1 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? extends java.util.List<?>>'">listClass</error>;
    Class<? extends List<? extends List<?>>> cls2 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">listClass</error>;
    Class<? super List<?>> clsS = <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? super java.util.List<?>>'">listClass</error>;
    Class<? super List> clsS1 = <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? super java.util.List>'">listClass</error>;
  }
}
