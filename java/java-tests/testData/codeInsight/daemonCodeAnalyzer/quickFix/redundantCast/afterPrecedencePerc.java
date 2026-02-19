// "Remove redundant cast" "true-preview"
class Test {
  {
    int i = 1;
    int j = 2;
    System.out.println(j * (i % j));
  }
}