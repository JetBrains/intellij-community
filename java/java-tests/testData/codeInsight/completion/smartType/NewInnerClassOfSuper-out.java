public class Zoo2 {
    static class Handler {}
}

class Zoo3 extends Zoo2 {
    class MyHandler extends Handler {}
}

class Zoo4 extends Zoo3 {
    {
        Handler handler = new MyHandler();<caret>
    }
}