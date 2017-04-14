import java.io.IOException;

class Test {
  /**
   * @throws IOException in some case
   * <warning descr="'throws' tag description is missing"><warning descr="Duplicate @throws or @exception tag for exception 'java.io.IOException'">@throws</warning></warning> IOException
   */
  void a() throws IOException{
    throw new IOException();
  }
}