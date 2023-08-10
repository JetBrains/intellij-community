
public class SubstringInAppend {
  static {
    StringBuilder sb = new StringBuilder();
    sb.append("xxx".<warning descr="Call to 'substring()' is redundant">substring</warning>(3, 5));
  }

  SubstringInAppend() {
    StringBuilder sb = new StringBuilder();
    sb.append("xxx".<warning descr="Call to 'substring()' is redundant">substring</warning>(3, 5));
  }

  public void doWork(StringBuilder sb) {
    sb.append("xxx".<warning descr="Call to 'substring()' is redundant">substring</warning>(3, 5));
  }
}
