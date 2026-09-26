package sample;

import static sample.Lambda2.<error descr="Cannot resolve symbol 'lambda'">lambda</error>;
class Sample {
  public static void main(String[] args) {
    <error descr="Cannot resolve method 'lambda' in 'Sample'">lambda</error>();
  }
}
