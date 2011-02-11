class Test <T>{
    {
        Test<Test<Test>> test = new Test<Test>();
        {
             test.put("");
            {
                test.get().get().get().<ref>get();
            }
        }
    }

    T get(){ return null; }
    void put(T x){}
}
