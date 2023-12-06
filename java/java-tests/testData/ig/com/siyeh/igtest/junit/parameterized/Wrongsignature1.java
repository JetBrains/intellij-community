@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
public class Wrongsignature1 {
   @org.junit.runners.Parameterized.Parameters
    static Integer <warning descr="Data provider method 'regExValues()' has an incorrect signature">regExValues</warning>() {
        return null;
    }
}