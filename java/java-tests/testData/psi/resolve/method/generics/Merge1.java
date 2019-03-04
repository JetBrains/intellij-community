class Test <T>{
    {
        Test<Test<Test>> test = new Test<Test>();
        {
             test.put("");
            {
                test.get().get().get().<caret>get();
            }
        }
    }

    T get(){ return null; }
    void put(T x){}
}
