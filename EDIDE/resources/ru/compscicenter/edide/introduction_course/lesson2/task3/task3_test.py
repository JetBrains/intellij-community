from test_helper import run_common_tests


if __name__ == '__main__':
    run_common_tests('''greetings = "greetings"
greetings = another value''', '''greetings = "greetings"
greetings =''', "You should redefine variable 'greetings'")