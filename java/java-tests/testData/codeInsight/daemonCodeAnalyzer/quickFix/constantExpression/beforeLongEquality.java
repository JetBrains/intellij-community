// "Replace '0x7fffffffffffffffL == 0x7ffffffffffffffeL' with constant value 'false'" "true-preview"
class Test {
  boolean result = 0x7fffffffffffffffL<caret> == 0x7ffffffffffffffeL;
}