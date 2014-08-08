from test_helper import run_common_tests

if __name__ == '__main__':
    # TODO check that what is added is a comment
    run_common_tests('''# This is the comment for the comments.py file
print("Hello!")  # this comment is for the second line

print("# this is not a comment")
# type here''',
                     '''# This is the comment for the comments.py file
print("Hello!")  # this comment is for the second line

print("# this is not a comment")
#''',
                     "You should type new comment")
