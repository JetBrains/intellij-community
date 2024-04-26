// "Move initializer to constructor" "false"
private final Integer i<caret> = 2;

void main() {
  foo(i);
}

void foo(Integer i) {
  System.out.println(i);
}