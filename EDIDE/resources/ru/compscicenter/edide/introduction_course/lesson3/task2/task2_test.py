from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.tenofhellos == "hellohellohellohellohellohellohellohellohellohello":
        passed()
    failed("Use multiplication")


if __name__ == '__main__':
    run_common_tests('''hello  = "hello"
tenofhellos = hello operator 10
print(tenofhellos)''',
                     '''hello  = "hello"
tenofhellos = hello  10
print(tenofhellos)''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)
    #TODO check mult operation used