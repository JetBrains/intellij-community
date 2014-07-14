import java.util.List;

class IDEA80386 {
  void foo(Class<List> listClass) {
    <error descr="Incompatible types. Found: 'java.lang.Class<java.util.List>', required: 'java.lang.Class<? extends java.util.List<?>>'">Class<? extends List<?>> cls = listClass;</error>
    Class < ?extends List > cls1 = listClass;
    <error descr="Incompatible types. Found: 'java.lang.Class<java.util.List>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">Class<? extends List<? extends List<?>>> cls2 = listClass;</error>
    Class<? super List<?>> clsS = listClass;
    Class<? super List> clsS1 = listClass;
  }

  void fooE(Class<? extends List> listClass) {
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? extends java.util.List<?>>'">Class<? extends List<?>> cls = listClass;</error>
    Class<? extends List> cls1 = listClass;
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">Class<? extends List<? extends List<?>>> cls2 = listClass;</error>
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? super java.util.List<?>>'">Class<? super List<?>> clsS = listClass;</error>
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.List>>', required: 'java.lang.Class<? super java.util.List>'">Class<? super List> clsS1 = listClass;</error>
  }

  void fooS(Class<? super List> listClass) {
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<? super java.util.List>>', required: 'java.lang.Class<? extends java.util.List<?>>'">Class<? extends List<?>> cls1 = listClass;</error>
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<? super java.util.List>>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">Class<? extends List<? extends List<?>>> cls2 = listClass;</error>
    Class<? super List<?>> clsS = listClass;
    Class<? super List> clsS1 = listClass;
  }

  void fooU(Class<?> listClass) {
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? extends java.util.List<?>>'">Class<? extends List<?>> cls1 = listClass;</error>
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? extends java.util.List<? extends java.util.List<?>>>'">Class<? extends List<? extends List<?>>> cls2 = listClass;</error>
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? super java.util.List<?>>'">Class<? super List<?>> clsS = listClass;</error>
    <error descr="Incompatible types. Found: 'java.lang.Class<capture<?>>', required: 'java.lang.Class<? super java.util.List>'">Class<? super List> clsS1 = listClass;</error>
  }
}
