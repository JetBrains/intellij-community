from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.hello_world == "HelloWorld":
        failed("Use one-space string ' ' in concatenation.")
    passed()

def test_value_2(path):
    file = import_file(path)
    if file.hello_world == "Hello World":
        passed()
    failed("Use + operator")


if __name__ == '__main__':
    run_common_tests('''hello = "Hello"
world = 'World'

hello_world = type here
print(hello_world)''',
                     '''hello = "Hello"
world = 'World'

hello_world =
print(hello_world)''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)
    test_value_2(path)

    #TODO: check concat used