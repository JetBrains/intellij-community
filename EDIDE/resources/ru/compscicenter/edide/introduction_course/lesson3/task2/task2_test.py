from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.tenofhellos == "hellohellohellohellohellohellohellohellohellohello":
        return "Bravo"
    return "Use multiplication"


if __name__ == '__main__':
    run_common_tests('''hello  = "hello"
tenofhellos = hello operator 10
print(tenofhellos)''',
                     '''hello  = "hello"
tenofhellos = hello  10
print(tenofhellos)''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    test_value(path)