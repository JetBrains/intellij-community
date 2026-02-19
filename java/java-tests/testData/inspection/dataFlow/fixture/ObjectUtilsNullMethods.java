package org.apache.commons.lang3;

class ObjectUtils {
  public static native boolean allNotNull(final Object... values);
  public static native boolean allNull(final Object... values);
  public static native boolean anyNotNull(final Object... values);
  public static native boolean anyNull(final Object... values);
}

class Test {
  void use(Object o1, Object o2, Object o3, Object o4) {
    if (ObjectUtils.allNull(o1, o2, o3, o4)) {
      if (<warning descr="Condition 'o1 == null' is always 'true'">o1 == null</warning>) {}
      if (<warning descr="Condition 'o2 == null' is always 'true'">o2 == null</warning>) {}
      if (<warning descr="Condition 'o3 == null' is always 'true'">o3 == null</warning>) {}
      if (<warning descr="Condition 'o4 == null' is always 'true'">o4 == null</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.allNull(o1, o2, o3, o4)' is always 'true'">ObjectUtils.allNull(o1, o2, o3, o4)</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.anyNull(o1, o2, o3, o4)' is always 'true'">ObjectUtils.anyNull(o1, o2, o3, o4)</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.anyNotNull(o1, o2, o3, o4)' is always 'false'">ObjectUtils.anyNotNull(o1, o2, o3, o4)</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.allNotNull(o1, o2, o3, o4)' is always 'false'">ObjectUtils.allNotNull(o1, o2, o3, o4)</warning>) {}
    }
    if (ObjectUtils.anyNull(o1, o2, o3, o4)) {
      if (o1 != null) {
        if (o2 != null) {
          if (o3 != null) {
            if (<warning descr="Condition 'o4 != null' is always 'false'">o4 != null</warning>) {}
          }
        }
      }
      if (ObjectUtils.allNull(o1, o2, o3, o4)) {}
      if (<warning descr="Condition 'ObjectUtils.anyNull(o1, o2, o3, o4)' is always 'true'">ObjectUtils.anyNull(o1, o2, o3, o4)</warning>) {}
      if (ObjectUtils.anyNotNull(o1, o2, o3, o4)) {}
      if (<warning descr="Condition 'ObjectUtils.allNotNull(o1, o2, o3, o4)' is always 'false'">ObjectUtils.allNotNull(o1, o2, o3, o4)</warning>) {}
    }
    if (ObjectUtils.anyNotNull(o1, o2, o3, o4)) {
      if (o1 == null) {
        if (o2 == null) {
          if (o3 == null) {
            if (<warning descr="Condition 'o4 == null' is always 'false'">o4 == null</warning>) {}
          }
        }
      }
      if (<warning descr="Condition 'ObjectUtils.allNull(o1, o2, o3, o4)' is always 'false'">ObjectUtils.allNull(o1, o2, o3, o4)</warning>) {}
      if (ObjectUtils.anyNull(o1, o2, o3, o4)) {}
      if (<warning descr="Condition 'ObjectUtils.anyNotNull(o1, o2, o3, o4)' is always 'true'">ObjectUtils.anyNotNull(o1, o2, o3, o4)</warning>) {}
      if (ObjectUtils.allNotNull(o1, o2, o3, o4)) {}
    }
    if (ObjectUtils.allNotNull(o1, o2, o3, o4)) {
      if (<warning descr="Condition 'o1 != null' is always 'true'">o1 != null</warning>) {}
      if (<warning descr="Condition 'o2 != null' is always 'true'">o2 != null</warning>) {}
      if (<warning descr="Condition 'o3 != null' is always 'true'">o3 != null</warning>) {}
      if (<warning descr="Condition 'o4 != null' is always 'true'">o4 != null</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.allNull(o1, o2, o3, o4)' is always 'false'">ObjectUtils.allNull(o1, o2, o3, o4)</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.anyNull(o1, o2, o3, o4)' is always 'false'">ObjectUtils.anyNull(o1, o2, o3, o4)</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.anyNotNull(o1, o2, o3, o4)' is always 'true'">ObjectUtils.anyNotNull(o1, o2, o3, o4)</warning>) {}
      if (<warning descr="Condition 'ObjectUtils.allNotNull(o1, o2, o3, o4)' is always 'true'">ObjectUtils.allNotNull(o1, o2, o3, o4)</warning>) {}
    }
  }
  
  void box(int i1, int i2, int i3, int i4) {
    if (<warning descr="Condition 'ObjectUtils.allNotNull(i1, i2, i3, i4)' is always 'true'">ObjectUtils.allNotNull(i1, i2, i3, i4)</warning>) {
      
    }
  }
}