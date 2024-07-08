import java.time.LocalDateTime;
import java.time.Month;

public class Test {
    private static class SomeClass {
        SomeClass(int... args) {
        }
    }

    private static final SomeClass someClassInstance = new SomeClass(
            2017,
            100000,
            100000,
            100000,
            100000,
            100000,
            100000);

    private static final LocalDateTime localDateTime = LocalDateTime.of(
            1000,
            Month.NOVEMBER,
            300000,
            10000,
            100000,
            5000);


    public void fooBarBaz(int... args) {
        fooBarBaz(
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000,
                100000);
    }
}