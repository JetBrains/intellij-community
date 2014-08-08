from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.exclamation == "!":
        passed()
    failed("Use negative index.")


if __name__ == '__main__':
    run_common_tests('''long_string = "This is a very long string!"
exclamation = type here
print (exclamation)''', '''long_string = "This is a very long string!"
exclamation =
print (exclamation)''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)

    #TODO: check that used negative index instead of positive