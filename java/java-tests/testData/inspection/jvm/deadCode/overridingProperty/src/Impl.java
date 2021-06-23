public class Impl implements In {
  public String getPr() {
    return null;
  }
  
  public void setPr(String pr) {
    System.out.println(pr);
  }

  public static void main(String[] args) {
    I impl = new Impl();
    impl.setPr("");
    System.out.println(impl.getPr());
  }
}