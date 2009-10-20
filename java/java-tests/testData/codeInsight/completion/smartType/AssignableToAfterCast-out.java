public class ZZZ {
       Object foo();

        {
            ZZZ z;
            ZZZ y = (ZZZ ) z.foo()<caret>
        }
    }