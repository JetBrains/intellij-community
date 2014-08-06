from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.number == 14.0:
        return "#study_plugin test OK"
    return "#study_plugin Please, reload the task and try again."


if __name__ == '__main__':
    run_common_tests('''number = 9.0

numer operator 5

''', '''number = 9.0

numer  5

''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    print(test_value(path))