@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class Wrongsignature2 {
   @org.junit.runners.Parameterized.Parameters
   public static Integer <warning descr="Data provider method 'regExValues()' has an incorrect signature">regExValues</warning>() {
        return null;
    }
}