// "Fix all ''equals()' called on classes which don't override it' problems in file" "true"
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicFix {

  StringBuilder sb1;

  @Nullable
  StringBuilder sb2;
  public void testField() {
    if (sb1.equals(sb2)) {
      System.out.println("1");
    }
  }
  public void testStringBuilder(StringBuilder sb1, StringBuilder sb2) {
    if (!sb1.e<caret>quals(sb2) && isaBoolean()) {
      System.out.println("Strange");
    }
  }

  public void testStringBuilderNullable(StringBuilder sb1, @Nullable StringBuilder sb2) {
    if (isaBoolean() || !sb1.equals(sb2) && isaBoolean()) {
      System.out.println("Strange");
    }
  }

  public void testStringBuffer(StringBuffer sb1, StringBuffer sb2) {
    if (!sb1.equals(sb2) && isaBoolean()) {
      System.out.println("Strange");
    }
  }

  public void testAtomicBoolean(AtomicBoolean a1, AtomicBoolean a2) {
    if (isaBoolean() || a1.equals(a2)) {
      System.out.println("Strange");
    }
  }

  private boolean isaBoolean() {
    return false;
  }

  public void testAtomicBooleanNullable(AtomicBoolean a1, @Nullable AtomicBoolean a2) {
    if (!a1.equals(a2) && isaBoolean()) {
      System.out.println("Strange");
    }
  }

  public void testAtomicInteger(AtomicInteger a1, AtomicInteger a2) {
    if (!a1.equals(a2)) {
      System.out.println("Strange");
    }
  }

  public void testAtomicLong(AtomicLong a1, AtomicLong a2) {
    if (isaBoolean() || a1.equals(a2)) {
      System.out.println("Strange");
    }
  }
}
