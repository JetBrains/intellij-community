from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.hello_world == "HelloWorld":
        return "#study_plugin Use one-space string ' ' in concatenation."
    return "#study_plugin test OK"


if __name__ == '__main__':
    run_common_tests('''hello = "hello"
world = "world"

hello_world = type here''',
                     '''hello = "hello"
world = "world"

hello_world =''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    print(test_value(path))