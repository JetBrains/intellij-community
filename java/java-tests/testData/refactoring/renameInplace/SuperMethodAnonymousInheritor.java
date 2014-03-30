class Demo {
    class MyEvent<T> {}
    interface MyEventListener<T> {
        void action(MyEvent<T> event);
    }

    class Driver {
        void method() {
            MyEventListener<Object> l = new MyEventListener<Object>() {
                public void ac<caret>tion(MyEvent<Object> event) {
                  
                }
            };
        }
    }
}