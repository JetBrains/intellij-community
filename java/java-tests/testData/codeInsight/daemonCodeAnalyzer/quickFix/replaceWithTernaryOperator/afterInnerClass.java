// "Replace with 'njc != null ?:'" "true-preview"
public class JC {
  class Inner {}

  public static void main(String[] args) {
    JC njc = Math.random() > 0.5 ? new JC() : null;
    System.out.println(njc != null ? njc.new Inner() : null);
  }
}