class FormattingTest {

    private static FormattingTest staticFoo;

    private FormattingTest instanceFoo;

    @SuppressWarnings({"AccessStaticViaInstance"})
    public void test() {

        // Long chained method calls with initial instance target - method calls are aligned to the first method
        // call place.
        instanceFoo.instanceMethodWithQuiteLongNameIndeed().staticMethodWithQuiteLongNameIndeed()
                   .instanceMethodWithQuiteLongNameIndeed().instanceMethodWithQuiteLongNameIndeed()
                   .staticMethodWithQuiteLongNameIndeed();

        // Long chained method calls with initial static target - method calls are aligned to the first method
        // call place.
        staticFoo.instanceMethodWithQuiteLongNameIndeed().staticMethodWithQuiteLongNameIndeed()
                 .instanceMethodWithQuiteLongNameIndeed().instanceMethodWithQuiteLongNameIndeed()
                 .staticMethodWithQuiteLongNameIndeed();

        // Interleaved method call chains - every sub-chain is aligned to its own target
        instanceFoo.instanceMethodWithQuiteLongNameIndeed().staticMethodWithQuiteLongNameIndeed()
                   .instanceMethodWithQuiteLongNameIndeed().instanceFoo.instanceMethodWithQuiteLongNameIndeed()
                                                                       .staticMethodWithQuiteLongNameIndeed()
                                                                       .instanceMethodWithQuiteLongNameIndeed();

        // Interleaved method call chains with manual line break - every sub-chain is aligned to its own
        // target and manual line break is preserved.
        instanceFoo.instanceMethodWithQuiteLongNameIndeed().staticMethodWithQuiteLongNameIndeed()
                .instanceFoo.instanceMethodWithQuiteLongNameIndeed().staticMethodWithQuiteLongNameIndeed()
                            .instanceMethodWithQuiteLongNameIndeed();

        // Chained calls with implicit target - method calls are not aligned and use continuation indent instead.
        instanceMethodWithQuiteLongNameIndeed().instanceMethodWithQuiteLongNameIndeed()
                .instanceMethodWithQuiteLongNameIndeed().instanceMethodWithQuiteLongNameIndeed();
    }

    public FormattingTest instanceMethodWithQuiteLongNameIndeed() {
        return instanceFoo;
    }

    public static FormattingTest staticMethodWithQuiteLongNameIndeed() {
        return staticFoo;
    }
}
