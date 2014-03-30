public class Bar {
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                <caret>
            }
        })
    }

}
