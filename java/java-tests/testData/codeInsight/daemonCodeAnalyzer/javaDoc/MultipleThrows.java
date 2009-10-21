import java.io.IOException;
class Test {
  /**
  * @throws IOException in some case
  * <warning>@throws</warning> IOException
  */
  void a() throws IOException{
    throw new IOException();
  }
}