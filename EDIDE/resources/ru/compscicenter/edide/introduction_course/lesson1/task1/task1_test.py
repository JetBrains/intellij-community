from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('print ("Hello, world! My name is type your name")',
                     'print ("Hello, world! My name is ")', "You should type your name")
