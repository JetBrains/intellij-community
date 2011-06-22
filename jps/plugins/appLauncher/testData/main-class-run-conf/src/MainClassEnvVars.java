import java.lang.System;

public class MainClassEnvVars {
  public static void main(String[] args) {
    System.out.println(System.getenv("ENV1"));
    System.out.println(System.getenv("ENV2"));
  }
}
